package com.example.trader.infra.redis.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
public class SafeRedisMessageListenerContainer extends RedisMessageListenerContainer {

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    @Override
    public void start() {
        try {
            super.start();
            log.info("[Redis Pub/Sub] listener container started");
        } catch (Exception e) {
            log.warn("[Redis Pub/Sub] start failed. app will continue without pub/sub.");
        }
    }

    @Override
    public void stop() {
        try {
            super.stop();
            log.info("[Redis Pub/Sub] listener container stopped");
        } catch (Exception e) {
            log.warn("[Redis Pub/Sub] stop failed. ignored.");
        }
    }
}