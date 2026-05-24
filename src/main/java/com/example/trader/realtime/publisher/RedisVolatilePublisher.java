package com.example.trader.realtime.publisher;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;

import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

//휘발성 데이터는 레디스 펍/섭으로 전파
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class RedisVolatilePublisher {

    private final ObjectProvider<RedisPubSubPublisher> redisPublisher;

    public void publish(RealtimeEnvelope envelope) {
        RedisPubSubPublisher publisher = redisPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publish(envelope);
        } else {
            log.debug("[VOLATILE] RedisPubSubPublisher not available (pubsub disabled), skip. subType={}", envelope.getSubType());
        }
    }
}