package com.example.trader.infra.kafka;

import com.example.trader.realtime.RealtimeEventDeduplicator;
import com.example.trader.realtime.message.RealtimeEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class KafkaConsumer {

    /**
     * Kafka 소비 역할 변경: 실시간 전파 → 이벤트 로그 수신 확인
     *
     * [이전 역할] Kafka 소비 → Redis Pub/Sub 재발행 → WebSocket broadcast
     * [현재 역할] Kafka 소비 → 이벤트 수신 확인 (감사 로그, 향후 팬아웃 확장 지점)
     *
     * 실시간 전파는 DegradableReliablePublisher에서 Redis Pub/Sub으로 직접 처리한다.
     * Kafka는 이벤트 저장 및 복구 용도이므로 consumer가 Redis로 재발행할 필요가 없다.
     *
     * Outbox 재발행 시 중복 수신 방지를 위해 eventId 기반 dedup 처리는 유지한다.
     */

    private final RealtimeEventDeduplicator deduplicator;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = "${realtime.kafka.topic:canvas-events}",
            groupId = "${realtime.kafka.group-id:canvas-broadcast-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(RealtimeEnvelope envelope) {
        if (envelope.getEventId() == null) {
            log.warn("[KAFKA-CONSUMER] eventId null, skipping. subType={}", envelope.getSubType());
            meterRegistry.counter("realtime.kafka.consume", "result", "skip_no_id").increment();
            return;
        }

        if (deduplicator.isDuplicate(envelope.getEventId())) {
            meterRegistry.counter("realtime.kafka.consume", "result", "duplicate").increment();
            return;
        }

        meterRegistry.counter("realtime.kafka.consume", "result", "received").increment();
        log.info("[KAFKA-CONSUMER] event received. eventId={}, subType={}, graphId={}",
                envelope.getEventId(), envelope.getSubType(), envelope.getGraphId());
    }
}
