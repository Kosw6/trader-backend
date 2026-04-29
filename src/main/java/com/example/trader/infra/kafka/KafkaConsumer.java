package com.example.trader.infra.kafka;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
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

    private final RedisPubSubPublisher redisPubSubPublisher;

    @KafkaListener(
            topics = "${realtime.kafka.topic:canvas-events}",
            groupId = "${realtime.kafka.group-id:canvas-broadcast-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(RealtimeEnvelope envelope) {
        redisPubSubPublisher.publish(envelope);
    }
}