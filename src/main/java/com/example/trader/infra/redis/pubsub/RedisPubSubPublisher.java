package com.example.trader.infra.redis.pubsub;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "realtime.redis.pubsub-enabled",
        havingValue = "true"
)
public class RedisPubSubPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(RealtimeEnvelope envelope) {
        try {
            String channel = "canvas:%d:%d".formatted(
                    envelope.getTeamId(),
                    envelope.getGraphId()
            );

            // publishedAt이 없으면 최초 발행 시점 기록
            RealtimeEnvelope stamped = envelope.getPublishedAt() != null
                    ? envelope
                    : envelope.toBuilder().publishedAt(System.currentTimeMillis()).build();

            String message = objectMapper.writeValueAsString(stamped);
            stringRedisTemplate.convertAndSend(channel, message);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis Pub/Sub message serialization failed", e);
        }
    }
}