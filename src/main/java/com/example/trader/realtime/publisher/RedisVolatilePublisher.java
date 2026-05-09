package com.example.trader.realtime.publisher;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;

import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

//휘발성 데이터는 레디스 펍/섭으로 전파
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class RedisVolatilePublisher {

    private final RedisPubSubPublisher redisPublisher;

    public void publish(RealtimeEnvelope envelope) {
        redisPublisher.publish(envelope);
    }
}