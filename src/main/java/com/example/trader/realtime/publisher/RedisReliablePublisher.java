package com.example.trader.realtime.publisher;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisReliablePublisher {

    private final RedisPubSubPublisher redisPubSubPublisher;

    public void publish(RealtimeEnvelope envelope) {
        redisPubSubPublisher.publish(envelope);
    }
}