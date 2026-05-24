package com.example.trader.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 서버별 Reliable 이벤트 WS 전파 dedup.
 *
 * <p>key 구조: {@code processed:reliable:{instanceId}:{eventId}}
 * <p>TTL: 5분
 *
 * <p>동작 흐름:
 * <pre>
 * Redis Pub/Sub 수신 → WS 브로드캐스트 → markPropagated()
 * Kafka consumer 수신 → hasBeenPropagated() 확인
 *   true  → 이미 Pub/Sub 경로로 전파됨 → skip
 *   false → Pub/Sub 누락 → WS 재전파 → markPropagated()
 * </pre>
 *
 * <p>Redis 장애 시 {@code hasBeenPropagated()} 는 false 반환 → 재전파 허용 (at-least-once).
 * 클라이언트 측 eventId Set으로 중복 렌더링 방지.
 */
@Slf4j
@Component
public class ReliablePropagationDedup {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "processed:reliable:";

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    public ReliablePropagationDedup(
            StringRedisTemplate redisTemplate,
            @Value("${app.instance-id:local}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    /**
     * 이 서버에서 해당 이벤트를 WS로 전파했음을 기록한다.
     */
    public void markPropagated(String eventId) {
        if (eventId == null) return;
        String key = buildKey(eventId);
        try {
            redisTemplate.opsForValue().set(key, "1", TTL);
            log.debug("[RELIABLE-DEDUP] marked. key={}", key);
        } catch (Exception e) {
            log.warn("[RELIABLE-DEDUP] mark failed. key={}, reason={}", key, e.getMessage());
        }
    }

    /**
     * 이 서버에서 이미 WS 전파한 이벤트인지 확인한다.
     * Redis 장애 시 false 반환 → 재전파 허용.
     */
    public boolean hasBeenPropagated(String eventId) {
        if (eventId == null) return false;
        String key = buildKey(eventId);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[RELIABLE-DEDUP] check failed, treating as not propagated. key={}, reason={}",
                    key, e.getMessage());
            return false;
        }
    }

    private String buildKey(String eventId) {
        return KEY_PREFIX + instanceId + ":" + eventId;
    }
}
