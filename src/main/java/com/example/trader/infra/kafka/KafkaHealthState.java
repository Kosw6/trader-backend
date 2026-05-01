package com.example.trader.infra.kafka;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class KafkaHealthState {

    private final AtomicBoolean available = new AtomicBoolean(true);

    public boolean isAvailable() {
        return available.get();
    }

    public void markUp() {
        available.set(true);
    }

    public void markDown() {
        available.set(false);
    }
}