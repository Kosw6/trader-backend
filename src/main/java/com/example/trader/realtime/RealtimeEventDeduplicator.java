package com.example.trader.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

//사용되는 분기 : 카프카 이벤트 소비시
//역할 : 복구등으로 중복 발행된 카프카 이벤트 확인
@Component
@RequiredArgsConstructor
public class RealtimeEventDeduplicator {

    private final StringRedisTemplate redisTemplate;

    public boolean isDuplicate(String eventId) {
        String key = "realtime:event:seen:" + eventId;

        Boolean isFirst = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(10));

        return Boolean.FALSE.equals(isFirst);
    }
}
