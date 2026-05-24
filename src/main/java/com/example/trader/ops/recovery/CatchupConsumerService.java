package com.example.trader.ops.recovery;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class CatchupConsumerService {

    private static final int MAX_METADATA_RETRIES = 10;
    private static final long RETRY_SLEEP_MS = 1000L;

    private final ConsumerFactory<String, RealtimeEnvelope> consumerFactory;
    private final ReplayStateTracker replayStateTracker;
    private final RecoveryReadinessManager recoveryReadinessManager;
    private final BroadcastConsumerService broadcastConsumerService;
    private final ObjectProvider<RedisPubSubPublisher> redisPubSubPublisher;

    public CatchupConsumerService(
            ConsumerFactory<String, RealtimeEnvelope> consumerFactory,
            ReplayStateTracker replayStateTracker,
            RecoveryReadinessManager recoveryReadinessManager,
            BroadcastConsumerService broadcastConsumerService,
            ObjectProvider<RedisPubSubPublisher> redisPubSubPublisher) {
        this.consumerFactory = consumerFactory;
        this.replayStateTracker = replayStateTracker;
        this.recoveryReadinessManager = recoveryReadinessManager;
        this.broadcastConsumerService = broadcastConsumerService;
        this.redisPubSubPublisher = redisPubSubPublisher;
    }

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${realtime.kafka.topic:canvas-events}")
    private String topic;

    @Value("${realtime.kafka.catchup-group-id:canvas-replay}")
    private String catchupGroupIdPrefix;

    @Value("${app.instance-id:${HOSTNAME:local}}")
    private String instanceId;

    private KafkaMessageListenerContainer<String, RealtimeEnvelope> catchupContainer;

    public synchronized void startCatchupContainer() {
        if (catchupContainer != null && catchupContainer.isRunning()) {
            log.info("[CATCHUP] already running instanceId={}", instanceId);
            return;
        }

        replayStateTracker.reset();

        Map<Integer, Long> endOffsetsExclusive = resolveEndOffsetsWithRetry(topic);
        replayStateTracker.setTargetOffsets(endOffsetsExclusive);

        if (!replayStateTracker.hasBacklog()) {
            log.info("[CATCHUP] no backlog detected topic={} endOffsets={} tracker={}",
                    topic, endOffsetsExclusive, replayStateTracker.summary());

            if (replayStateTracker.markCompletedOnce()) {
                recoveryReadinessManager.onCatchupCompleted();
                broadcastConsumerService.startBroadcastContainer();
            }
            return;
        }

        ContainerProperties properties = new ContainerProperties(topic);
        properties.setGroupId(catchupGroupIdPrefix + "-" + instanceId);
        properties.setPollTimeout(Duration.ofSeconds(1).toMillis());

        properties.setMessageListener(
                (org.springframework.kafka.listener.MessageListener<String, RealtimeEnvelope>) record -> {
                    RealtimeEnvelope envelope = record.value();

                    log.info("[CATCHUP] consume instanceId={} partition={} offset={} key={} type={} subType={} teamId={} graphId={}",
                            instanceId,
                            record.partition(),
                            record.offset(),
                            record.key(),
                            envelope != null ? envelope.getType() : null,
                            envelope != null ? envelope.getSubType() : null,
                            envelope != null ? envelope.getTeamId() : null,
                            envelope != null ? envelope.getGraphId() : null);

                    if (envelope != null) {
                        RedisPubSubPublisher publisher = redisPubSubPublisher.getIfAvailable();
                        if (publisher != null) {
                            publisher.publish(envelope);
                        } else {
                            log.warn("[CATCHUP] RedisPubSubPublisher not available (pubsub disabled), skip publish. subType={}", envelope.getSubType());
                        }
                    }

                    replayStateTracker.onReplay(record.partition(), record.offset());

                    if (replayStateTracker.isCatchupCompleted()
                            && replayStateTracker.markCompletedOnce()) {

                        log.info("[CATCHUP] completed instanceId={} {}",
                                instanceId, replayStateTracker.summary());

                        stopCatchupContainer();
                        recoveryReadinessManager.onCatchupCompleted();
                        broadcastConsumerService.startBroadcastContainer();
                    }
                }
        );

        catchupContainer = new KafkaMessageListenerContainer<>(consumerFactory, properties);
        catchupContainer.start();

        log.info("[CATCHUP] container started instanceId={} groupId={} endOffsets={} tracker={}",
                instanceId,
                catchupGroupIdPrefix + "-" + instanceId,
                endOffsetsExclusive,
                replayStateTracker.summary());
    }

    public synchronized void stopCatchupContainer() {
        if (catchupContainer != null) {
            try {
                if (catchupContainer.isRunning()) {
                    catchupContainer.stop();
                    log.info("[CATCHUP] container stopped instanceId={}", instanceId);
                }
            } finally {
                catchupContainer = null;
            }
        }
    }

    public synchronized boolean isRunning() {
        return catchupContainer != null && catchupContainer.isRunning();
    }

    private Map<Integer, Long> resolveEndOffsetsWithRetry(String topic) {
        for (int attempt = 1; attempt <= MAX_METADATA_RETRIES; attempt++) {
            try {
                Map<Integer, Long> offsets = resolveEndOffsets(topic);

                log.info("[CATCHUP] resolved end offsets topic={} attempt={}/{} offsets={}",
                        topic, attempt, MAX_METADATA_RETRIES, offsets);

                return offsets;

            } catch (Exception e) {
                if (isRetryableTopicMetadataException(e)) {
                    log.warn("[CATCHUP] topic metadata not ready topic={} retry={}/{} cause={}",
                            topic, attempt, MAX_METADATA_RETRIES, rootCauseMessage(e));
                    sleepSilently(RETRY_SLEEP_MS);
                    continue;
                }

                throw new IllegalStateException("Failed to resolve end offsets topic=" + topic, e);
            }
        }

        log.info("[CATCHUP] topic still not found after retries. treating as empty backlog topic={}", topic);
        return Map.of();
    }

    private Map<Integer, Long> resolveEndOffsets(String topic) throws Exception {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bootstrap.servers", bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(configs)) {
            var topicDescription = adminClient
                    .describeTopics(java.util.List.of(topic))
                    .allTopicNames()
                    .get()
                    .get(topic);

            Map<TopicPartition, OffsetSpec> request = new LinkedHashMap<>();

            for (var partitionInfo : topicDescription.partitions()) {
                TopicPartition topicPartition =
                        new TopicPartition(topic, partitionInfo.partition());
                request.put(topicPartition, OffsetSpec.latest());
            }

            ListOffsetsResult listOffsetsResult = adminClient.listOffsets(request);

            Map<Integer, Long> result = new LinkedHashMap<>();

            for (TopicPartition topicPartition : request.keySet()) {
                KafkaFuture<ListOffsetsResult.ListOffsetsResultInfo> future =
                        listOffsetsResult.partitionResult(topicPartition);

                result.put(topicPartition.partition(), future.get().offset());
            }

            return result;
        }
    }

    private boolean isRetryableTopicMetadataException(Throwable t) {
        Throwable cur = t;

        while (cur != null) {
            if (cur instanceof UnknownTopicOrPartitionException) {
                return true;
            }
            cur = cur.getCause();
        }

        return false;
    }

    private String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        Throwable last = t;

        while (cur != null) {
            last = cur;
            cur = cur.getCause();
        }

        return last.getClass().getSimpleName() + ": " + last.getMessage();
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kafka metadata", e);
        }
    }
}