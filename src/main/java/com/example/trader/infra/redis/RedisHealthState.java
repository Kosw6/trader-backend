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

    private static final int FAILURE_THRESHOLD = 3; // N번 실패 시 down

    public RedisHealthState(StringRedisTemplate redis) {
        this.redis = redis;

        // 초기 상태 확인
        try {
            redis.getConnectionFactory().getConnection().ping();
            available.set(true);
        } catch (Exception e) {
            available.set(false);
            log.warn("[REDIS] initial ping failed → start in DOWN state");
        }
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
            log.info("[REDIS] marked UP (recovered)");
        }
    }

    // 🔥 선택: 주기적 health check (복구 감지용)
    public void check() {
        try {
            redis.getConnectionFactory().getConnection().ping();
            markUp();
        } catch (Exception e) {
            markDown();
        }
    }

}
