package com.example.trader.realtime;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 현재 서버 프로세스에서 WebSocket 전파를 선점한 RELIABLE 이벤트를 기억한다.
 * 서버가 재시작되면 기록이 사라지는 best-effort 중복 억제 장치이며,
 * 내구성이나 시스템 전체의 exactly-once 처리를 보장하지 않는다.
 */
@Component
public class LocalReliableEventDeduplicator {

    private final Cache<String, Boolean> claimedEventIds;

    public LocalReliableEventDeduplicator(
            @Value("${realtime.reliable.dedup.maximum-size:100000}") long maximumSize,
            @Value("${realtime.reliable.dedup.ttl:10m}") Duration ttl) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("reliable dedup maximum-size must be positive");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("reliable dedup ttl must be positive");
        }

        this.claimedEventIds = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .build();
    }

    /**
     * 같은 JVM 안에서 하나의 수신 경로만 해당 이벤트를 처리하도록 원자적으로 선점한다.
     */
    public boolean tryClaim(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("RELIABLE eventId must not be blank");
        }
        return claimedEventIds.asMap().putIfAbsent(eventId, Boolean.TRUE) == null;
    }

    /**
     * 로컬 큐 적재에 실패한 이벤트를 Redis 또는 Kafka 경로가 다시 처리할 수 있게 한다.
     */
    public void release(String eventId) {
        if (eventId != null) {
            claimedEventIds.invalidate(eventId);
        }
    }
}
