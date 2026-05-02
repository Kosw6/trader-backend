package com.example.trader.infra.kafka;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.RealtimeEventDeduplicator;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
//멀티에서만 등록
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "realtime.kafka.enabled",
        havingValue = "true"
)
public class KafkaConsumer {
    /**흐름 : Reliable -> 이벤트 특성에 따라 처리, 상태관련은 레디스 저장 ->카프카 발행 -> 소비 후 레디스 펍/섭 발행
     *  if)카프카 다운 후 db outbox기반 복구시에 중복 전파의 가능성이 있기에 소비 시점에 eventId로 중복처리
      */
    private final RedisPubSubPublisher redisPubSubPublisher;
    private final RealtimeEventDeduplicator deduplicator;

    @KafkaListener(
            topics = "${realtime.kafka.topic:canvas-events}",
            groupId = "${realtime.kafka.group-id:canvas-broadcast-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(RealtimeEnvelope envelope) {

        // 🔥 1. eventId 없으면 그냥 처리 (혹은 로그)
        if (envelope.getEventId() == null) {
            redisPubSubPublisher.publish(envelope);
            return;
        }

        // 🔥 2. 중복 체크
        if (deduplicator.isDuplicate(envelope.getEventId())) {
            return;
        }

        // 🔥 3. 정상 전파
        redisPubSubPublisher.publish(envelope);
    }
}