package com.example.trader.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ServerStateManager {

    private final AtomicBoolean up = new AtomicBoolean(true);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public boolean isUp() {
        return up.get();
    }

    public boolean isReady() {
        return ready.get();
    }

    public boolean isDraining() {
        return draining.get();
    }

    public void markUp(boolean value) {
        up.set(value);
        log.info("[SERVER_STATE] up={}", value);
    }

    public void markReady(boolean value) {
        ready.set(value);
        log.info("[SERVER_STATE] ready={}", value);
    }

    public void markDraining(boolean value) {
        draining.set(value);
        log.info("[SERVER_STATE] draining={}", value);
    }

    public String snapshot() {
        return "up=" + up.get() + ", ready=" + ready.get() + ", draining=" + draining.get();
    }
}