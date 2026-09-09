package com.example.trader.infra.kafka;

import com.example.trader.realtime.ReliablePropagationDedup;
import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class KafkaConsumer {

    /**
     * Kafka consumer 역할: Reliable 이벤트 누락 보정 경로.
     *
     * <p>Redis Pub/Sub은 fire-and-forget 구조이므로 서버 다운·순간 단절 구간에서
     * Reliable 이벤트가 누락될 수 있다.
     * Kafka는 이 누락을 보정하는 2차 경로로 동작한다.
     *
     * <p>동작 흐름:
     * <pre>
     * Kafka consumer 수신
     *   → processed:reliable:{instanceId}:{eventId} 확인
     *   → key 있음: Redis Pub/Sub으로 이미 전파됨 → skip
     *   → key 없음: 누락된 이벤트 → WS 재전파 + dedup key 기록
     * </pre>
     *
     * <p>groupId는 서버별 고유 ID를 사용한다.
     * 공통 groupId 사용 시 partition 분배로 인해 각 서버가 전체 이벤트를
     * 수신하지 못해 누락 보정이 불가능해진다.
     */

    private final ReliablePropagationDedup propagationDedup;
    private final ReliableInboundHandler reliableInboundHandler;

    @KafkaListener(
            topics = "${realtime.kafka.topic:canvas-events}",
            groupId = "reliable-replay-${app.instance-id:local}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, RealtimeEnvelope> record) {
        RealtimeEnvelope envelope = record.value();

        if (envelope.getEventId() == null) {
            log.warn("[KAFKA-CONSUMER] eventId null, skipping. subType={}", envelope.getSubType());
            return;
        }

        // Kafka 복구 대상은 Outbox에 기록된 RELIABLE 이벤트뿐이다.
        if (envelope.getType() != RealtimeType.RELIABLE) {
            return;
        }

        // dedup 확인: 이 서버가 Redis Pub/Sub으로 이미 전파했으면 skip
        if (propagationDedup.hasBeenPropagated(envelope.getEventId())) {
            log.info("[KAFKA-CONSUMER] already propagated via pub/sub, skip. eventId={}", envelope.getEventId());
            return;
        }

        // Redis Pub/Sub 누락 감지 → WS 재전파
        // publishedAt 우선: 원본 발행 시점 기준 end-to-end lag
        // 없으면 record.timestamp() fallback: Kafka 브로커 수신 시점 기준
        long baseline = envelope.getPublishedAt() != null
                ? envelope.getPublishedAt()
                : record.timestamp();
        long lagMs = System.currentTimeMillis() - baseline;
        log.info("[KAFKA-CONSUMER] pub/sub miss detected. replaying. eventId={}, subType={}, graphId={}, lagMs={}",
                envelope.getEventId(), envelope.getSubType(), envelope.getGraphId(), lagMs);

        reliableInboundHandler.handle(envelope);
        // handle() 내부에서 markPropagated() 호출됨
    }
}
