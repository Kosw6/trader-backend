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

            String message = objectMapper.writeValueAsString(envelope);
            stringRedisTemplate.convertAndSend(channel, message);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Redis Pub/Sub message serialization failed", e);
        }
    }
}