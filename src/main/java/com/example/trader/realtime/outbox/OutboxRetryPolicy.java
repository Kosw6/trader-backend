package com.example.trader.realtime.outbox;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/** 횟수는 대기 간격 계산에 사용하고 종료 여부는 고정된 3분 기한으로 판단한다. */
final class OutboxRetryPolicy {
    private OutboxRetryPolicy() {}

    static LocalDateTime nextAttempt(LocalDateTime now, int attempts, LocalDateTime deadline) {
        return nextAttempt(now, attempts, deadline, ThreadLocalRandom.current().nextDouble());
    }

    static LocalDateTime nextAttempt(LocalDateTime now, int attempts, LocalDateTime deadline, double jitter) {
        long baseMillis = Math.min(30_000L, 5_000L << Math.min(3, Math.max(0, attempts - 1)));
        long delayMillis = baseMillis + (long) (baseMillis * 0.2 * jitter);
        LocalDateTime next = now.plusNanos(delayMillis * 1_000_000L);
        return deadline != null && next.isAfter(deadline) ? deadline : next;
    }
}
