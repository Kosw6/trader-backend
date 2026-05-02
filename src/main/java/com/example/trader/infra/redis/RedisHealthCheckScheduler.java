package com.example.trader.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthCheckScheduler {

    private final RedisHealthState redisHealthState;

    @Scheduled(fixedDelay = 2000)
    public void redisHealthCheck() {
        redisHealthState.check();
    }
}
