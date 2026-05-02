package com.example.trader.infra.redis.pubsub;

import com.example.trader.infra.redis.RedisHealthState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RealtimeHealthCheckScheduler
 * = 관측/상태 판단 담당
 *
 * RedisPubSubRecoveryScheduler
 * = Redis Pub/Sub 런타임 제어 담당
 *
 * */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "realtime.redis.pubsub-enabled",
        havingValue = "true"
)
public class RedisPubSubRecoveryScheduler {

    private final RedisHealthState redisHealthState;
    private final RedisMessageListenerContainer container;

    @Scheduled(fixedDelay = 5000)
    public void recover() {

        boolean available = redisHealthState.isAvailable();

        if (available && !container.isRunning()) {
            try {
                container.start();
                log.info("[Redis Pub/Sub] recovered and started");
            } catch (Exception e) {
                log.warn("[Redis Pub/Sub] start failed");
            }
        }

        if (!available && container.isRunning()) {
            try {
                container.stop();
                log.warn("[Redis Pub/Sub] stopped due to Redis down");
            } catch (Exception e) {
                log.warn("[Redis Pub/Sub] stop failed");
            }
        }
    }
}
