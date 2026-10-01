package com.example.trader.infra.kafka;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDateTime;

@Component
public class KafkaHealthState {

    private final AtomicBoolean available = new AtomicBoolean(true);
    private boolean probeHealthy;
    private volatile LocalDateTime confirmedRecoveryAt;

    public boolean isAvailable() {
        return available.get();
    }

    public void markUp() {
        available.set(true);
    }

    public synchronized void markDown() {
        available.set(false);
        probeHealthy = false;
    }

    /** 최초 성공 또는 DOWN 이후 성공한 메타데이터 검사만 복구 시각을 갱신한다. */
    public synchronized void confirmProbe(boolean healthy, LocalDateTime checkedAt) {
        if (!healthy) {
            markDown();
            return;
        }
        if (!probeHealthy) {
            confirmedRecoveryAt = checkedAt;
        }
        probeHealthy = true;
        available.set(true);
    }

    public LocalDateTime confirmedRecoveryAt() {
        return confirmedRecoveryAt;
    }
}
