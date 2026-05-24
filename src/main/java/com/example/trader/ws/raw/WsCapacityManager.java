package com.example.trader.ws.raw;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsCapacityManager {

    private final StringRedisTemplate redisTemplate;

    // Gateway WsShardRouter와 동일한 키 포맷
    private static final String ACTIVE_KEY   = "ws:group:%d:active:%s";
    private static final String RESERVED_KEY = "ws:group:%d:reserved:%s";

    // WS 연결 성공 시 호출: reserved -1, active +1
    public void confirm(long groupId, String instanceId) {
        String activeKey   = activeKey(groupId, instanceId);
        String reservedKey = reservedKey(groupId, instanceId);

        redisTemplate.opsForValue().increment(activeKey);
        redisTemplate.opsForValue().decrement(reservedKey);

        log.debug("[WS-CAPACITY] confirm groupId={} instance={}", groupId, instanceId);
    }

    // WS 연결 종료 시 호출: active -1
    public void release(long groupId, String instanceId) {
        String activeKey = activeKey(groupId, instanceId);
        Long remaining = redisTemplate.opsForValue().decrement(activeKey);

        // 카운터가 음수가 되지 않도록 보정
        if (remaining != null && remaining < 0) {
            redisTemplate.opsForValue().set(activeKey, "0");
        }

        log.debug("[WS-CAPACITY] release groupId={} instance={} remaining={}", groupId, instanceId, remaining);
    }

    private String activeKey(long groupId, String instanceId) {
        return String.format(ACTIVE_KEY, groupId, instanceId);
    }

    private String reservedKey(long groupId, String instanceId) {
        return String.format(RESERVED_KEY, groupId, instanceId);
    }
}
