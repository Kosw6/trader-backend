package com.example.trader.infra.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class RedisHealthState {

    private final StringRedisTemplate redis;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    private static final int FAILURE_THRESHOLD = 3;

    public RedisHealthState(StringRedisTemplate redis) {
        this.redis = redis;
        check();
    }

    public boolean isAvailable() {
        return available.get();
    }

    public void markDown() {
        int fail = failureCount.incrementAndGet();

        if (fail >= FAILURE_THRESHOLD && available.compareAndSet(true, false)) {
            log.warn("[REDIS] marked DOWN after {} failures", fail);
        }
    }

    public void markUp() {
        failureCount.set(0);

        if (available.compareAndSet(false, true)) {
            log.info("[REDIS] marked UP");
        }
    }

    public boolean check() {
        try {
            var connectionFactory = redis.getConnectionFactory();

            if (connectionFactory == null) {
                markDown();
                return false;
            }

            try (var connection = connectionFactory.getConnection()) {
                connection.ping();
            }

            markUp();
            return true;

        } catch (Exception e) {
            markDown();
            return false;
        }
    }
}