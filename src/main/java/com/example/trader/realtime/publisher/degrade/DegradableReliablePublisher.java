package com.example.trader.realtime.publisher.degrade;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.RealtimeHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.outbox.RealtimeOutboxService;
import com.example.trader.realtime.publisher.GrpcVolatilePublisher;
import com.example.trader.realtime.publisher.HttpVolatilePublisher;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Primary
public class DegradableReliablePublisher implements ReliablePublisher {

    /**
     * 두 가지 목적이 완전히 분리된다.
     *
     * [실시간 전파] Redis Pub/Sub → WebSocket (항상)
     *   Redis 장애 시: gRPC → HTTP 순으로 relay fallback
     *
     * [이벤트 로그] Kafka 비동기 저장 (항상)
     *   Kafka 장애 시: Outbox 저장 → 복구 후 재발행
     *
     * Kafka 장애가 실시간 전파에 영향을 주지 않는다.
     * Redis 장애가 이벤트 로그 저장에 영향을 주지 않는다.
     */

    private final RealtimeHealthState healthState;
    private final RealtimeOutboxService outboxService;

    private final ObjectProvider<RedisPubSubPublisher> redisPubSubPublisher;
    private final ObjectProvider<KafkaReliablePublisher> kafkaPublisher;
    private final ObjectProvider<GrpcVolatilePublisher> grpcRelay;
    private final ObjectProvider<HttpVolatilePublisher> httpRelay;

    private final MeterRegistry meterRegistry;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();

        // 1. 실시간 전파: Redis Pub/Sub (항상)
        publishRedisOrSkip(event);

        // 2. 이벤트 로그: Kafka 저장 (항상, 장애 시 Outbox)
        publishKafkaOrOutbox(event);
    }

    @Override
    public void publishOrThrow(RealtimeEnvelope envelope) throws Exception {
        // Outbox 재발행 스케줄러 전용 — Kafka 로그 저장만 수행
        // Redis 전파는 최초 publish() 시점에 이미 처리됨
        RealtimeEnvelope event = envelope.ensureEventId();
        publishKafkaOrThrow(event);
    }

    // ── Redis: 실시간 전파 ────────────────────────────────────────────────

    private void publishRedisOrSkip(RealtimeEnvelope event) {
        meterRegistry.counter("realtime.redis.publish", "result", "attempt").increment();

        try {
            meterRegistry.timer("realtime.redis.publish.latency")
                    .record(() -> publishRedisOrThrow(event));

            meterRegistry.counter("realtime.redis.publish", "result", "success").increment();

        } catch (Exception e) {
            meterRegistry.counter("realtime.redis.publish", "result", "failed").increment();

            log.warn("[REALTIME] Redis Pub/Sub failed, trying relay. eventId={}, reason={}",
                    event.getEventId(), e.getMessage());
            relayOrSkip(event);
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

    private void relayOrSkip(RealtimeEnvelope event) {
        meterRegistry.counter("realtime.relay.attempt").increment();
        if (healthState.isGrpcAvailable()) {
            GrpcVolatilePublisher grpc = grpcRelay.getIfAvailable();
            if (grpc != null) {
                meterRegistry.timer("realtime.relay.latency", "path", "grpc")
                        .record(() -> grpc.publish(event));
                meterRegistry.counter("realtime.relay", "path", "grpc").increment();
                log.warn("[REALTIME-RELAY] relayed via gRPC. eventId={}", event.getEventId());
                return;
            }
        }
        if (healthState.isHttpAvailable()) {
            HttpVolatilePublisher http = httpRelay.getIfAvailable();
            if (http != null) {
                meterRegistry.timer("realtime.relay.latency", "path", "http")
                        .record(() -> http.publish(event));
                meterRegistry.counter("realtime.relay", "path", "http").increment();
                log.warn("[REALTIME-RELAY] relayed via HTTP. eventId={}", event.getEventId());
                return;
            }
        }
        meterRegistry.counter("realtime.relay", "path", "dropped").increment();
        log.warn("[REALTIME-RELAY] no relay available. eventId={} dropped.", event.getEventId());
    }

    // ── Kafka: 이벤트 로그 저장 ──────────────────────────────────────────

    private void publishKafkaOrOutbox(RealtimeEnvelope event) {
        try {
            meterRegistry.counter("realtime.kafka.publish", "result", "attempt").increment();
            meterRegistry.timer("realtime.kafka.publish.latency")
                    .record(() -> {
                        try {
                            publishKafkaOrThrow(event);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            meterRegistry.counter("realtime.kafka.publish", "result", "success").increment();

        } catch (Exception e) {
            meterRegistry.counter("realtime.kafka.publish", "result", "failed").increment();

            try {
                meterRegistry.timer("realtime.outbox.save.latency")
                        .record(() -> outboxService.savePending(event));

                meterRegistry.counter("realtime.outbox.save", "result", "success").increment();

                log.warn("[REALTIME-OUTBOX] Kafka publish failed. saved to outbox. eventId={}, reason={}",
                        event.getEventId(), e.getMessage());

            } catch (Exception outboxException) {
                meterRegistry.counter("realtime.outbox.save", "result", "failed").increment();

                log.error("[REALTIME-OUTBOX] Kafka publish failed and outbox save also failed. eventId={}, kafkaReason={}, outboxReason={}",
                        event.getEventId(), e.getMessage(), outboxException.getMessage());
            }
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
