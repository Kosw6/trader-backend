package com.example.trader.realtime.publisher.degrade;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.health.RealtimeHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.outbox.RealtimeOutboxService;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Primary
public class DegradableReliablePublisher implements ReliablePublisher {

    private final RealtimeHealthState healthState;
    private final RealtimeOutboxService outboxService;

    private final ObjectProvider<RedisPubSubPublisher> redisPubSubPublisher;
    private final ObjectProvider<KafkaReliablePublisher> kafkaPublisher;

    @Value("${realtime.reliable.route-mode:redis-only}")
    private String routeMode;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();

        publishRedisOrSkip(event);

        if (!"kafka".equalsIgnoreCase(routeMode)) {
            return;
        }

        publishKafkaOrOutbox(event);
    }

    @Override
    public void publishOrThrow(RealtimeEnvelope envelope) throws Exception {
        RealtimeEnvelope event = envelope.ensureEventId();

        publishRedisOrThrow(event);

        if ("kafka".equalsIgnoreCase(routeMode)) {
            publishKafkaOrThrow(event);
        }
    }

    private void publishRedisOrSkip(RealtimeEnvelope event) {
        try {
            publishRedisOrThrow(event);
        } catch (Exception e) {
            log.warn("[REALTIME] Redis Pub/Sub publish failed. eventId={}, reason={}",
                    event.getEventId(), e.getMessage());
        }
    }

    private void publishRedisOrThrow(RealtimeEnvelope event) {
        log.info("[RELIABLE-REDIS-PUBLISH] eventId={}, subType={}",
                event.getEventId(), event.getSubType());
        if (!healthState.isRedisAvailable()) {
            throw new IllegalStateException("Redis unavailable");
        }

        RedisPubSubPublisher publisher = redisPubSubPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("RedisPubSubPublisher bean not available");
        }

        publisher.publish(event);
    }

    private void publishKafkaOrOutbox(RealtimeEnvelope event) {
        try {
            publishKafkaOrThrow(event);
        } catch (Exception e) {
            outboxService.savePending(event);

            log.warn("[REALTIME-OUTBOX] Kafka publish failed. saved to outbox. eventId={}, reason={}",
                    event.getEventId(), e.getMessage());
        }
    }

    private void publishKafkaOrThrow(RealtimeEnvelope event) throws Exception {
        if (!healthState.isKafkaAvailable()) {
            throw new IllegalStateException("Kafka unavailable");
        }

        KafkaReliablePublisher publisher = kafkaPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("KafkaReliablePublisher bean not available");
        }

        publisher.publishOrThrow(event);
    }
}