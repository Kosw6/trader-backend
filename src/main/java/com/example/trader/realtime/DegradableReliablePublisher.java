package com.example.trader.realtime;

import com.example.trader.infra.kafka.KafkaHealthState;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.example.trader.realtime.publisher.RedisReliablePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class DegradableReliablePublisher implements ReliablePublisher {

    private final KafkaReliablePublisher kafkaReliablePublisher;
    private final RedisReliablePublisher redisReliablePublisher;
    private final KafkaHealthState kafkaHealthState;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        if (!kafkaHealthState.isAvailable()) {
            log.warn("[REALTIME-DEGRADED] Kafka unavailable. fallback to Redis. subType={}", envelope.getSubType());
            redisReliablePublisher.publish(envelope);
            return;
        }

        try {
            kafkaReliablePublisher.publishOrThrow(envelope);
            kafkaHealthState.markUp();
        } catch (Exception e) {
            kafkaHealthState.markDown();

            log.warn("[REALTIME-DEGRADED] Kafka publish failed. fallback to Redis. subType={}, reason={}",
                    envelope.getSubType(), e.getMessage());

            redisReliablePublisher.publish(envelope);
        }
    }
}