package com.example.trader.recovery;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.beans.factory.annotation.Value;
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
@RequiredArgsConstructor
public class CatchupConsumerService {

    private static final int MAX_METADATA_RETRIES = 10;
    private static final long RETRY_SLEEP_MS = 1000L;

    private final ConsumerFactory<String, CanvasEventEnvelope> consumerFactory;
    private final ReplayStateTracker replayStateTracker;
    private final RecoveryReadinessManager recoveryReadinessManager;
    private final BroadcastConsumerService broadcastConsumerService;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topic.canvas-events:canvas-events}")
    private String topic;

    @Value("${app.kafka.catchup-group-id:canvas-replay}")
    private String catchupGroupIdPrefix;

    @Value("${app.instance-id}")
    private String instanceId;

    private KafkaMessageListenerContainer<String, CanvasEventEnvelope> catchupContainer;

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

        properties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, CanvasEventEnvelope>) record -> {
            CanvasEventEnvelope event = record.value();

            log.info("[CATCHUP] raw-consume instanceId={} partition={} offset={} key={} groupId={} entityId={} version={}",
                    instanceId,
                    record.partition(),
                    record.offset(),
                    record.key(),
                    event != null ? event.getGroupId() : null,
                    event != null ? event.getEntityId() : null,
                    event != null ? event.getVersion() : null);

            replayStateTracker.onReplay(record.partition(), record.offset());

            if (replayStateTracker.isCatchupCompleted() && replayStateTracker.markCompletedOnce()) {
                log.info("[CATCHUP] completed instanceId={} {}",
                        instanceId,
                        replayStateTracker.summary());

                stopCatchupContainer();
                recoveryReadinessManager.onCatchupCompleted();
                broadcastConsumerService.startBroadcastContainer();
            }
        });

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
                    log.warn("[CATCHUP] topic metadata not ready yet topic={} retry={}/{} cause={}",
                            topic, attempt, MAX_METADATA_RETRIES, rootCauseMessage(e));
                    sleepSilently(RETRY_SLEEP_MS);
                    continue;
                }

                throw new IllegalStateException("Failed to resolve end offsets for topic=" + topic, e);
            }
        }

        log.info("[CATCHUP] topic still not found after retries. treating as empty backlog. topic={}", topic);
        return Map.of();
    }

    private Map<Integer, Long> resolveEndOffsets(String topic) throws Exception {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bootstrap.servers", bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(configs)) {
            DescribeTopicsResult describeTopicsResult = adminClient.describeTopics(java.util.List.of(topic));
            var topicDescription = describeTopicsResult.allTopicNames().get().get(topic);

            Map<TopicPartition, OffsetSpec> request = new LinkedHashMap<>();
            for (var partitionInfo : topicDescription.partitions()) {
                TopicPartition tp = new TopicPartition(topic, partitionInfo.partition());
                request.put(tp, OffsetSpec.latest());
            }

            ListOffsetsResult listOffsetsResult = adminClient.listOffsets(request);

            Map<Integer, Long> result = new LinkedHashMap<>();
            for (TopicPartition tp : request.keySet()) {
                KafkaFuture<ListOffsetsResult.ListOffsetsResultInfo> future =
                        listOffsetsResult.partitionResult(tp);
                long offset = future.get().offset();
                result.put(tp.partition(), offset);
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