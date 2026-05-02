package com.example.trader.realtime.health;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RealtimeHealthState {

    private final AtomicBoolean kafkaAvailable = new AtomicBoolean(false);
    private final AtomicBoolean grpcAvailable = new AtomicBoolean(false);
    private final AtomicBoolean httpAvailable = new AtomicBoolean(false);
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    public boolean isKafkaAvailable() {
        return kafkaAvailable.get();
    }

    public boolean isGrpcAvailable() {
        return grpcAvailable.get();
    }

    public boolean isHttpAvailable() {
        return httpAvailable.get();
    }

    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }

    public void setKafkaAvailable(boolean available) {
        kafkaAvailable.set(available);
    }

    public void setGrpcAvailable(boolean available) {
        grpcAvailable.set(available);
    }

    public void setHttpAvailable(boolean available) {
        httpAvailable.set(available);
    }

    public void setRedisAvailable(boolean available) {
        redisAvailable.set(available);
    }
}