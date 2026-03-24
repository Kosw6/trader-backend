package com.example.trader.edit;

import com.example.trader.edit.dto.DraftEditState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DraftRedisStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration TTL = Duration.ofHours(1);

    public void save(DraftEditState state) {
        try {
            String key = draftKey(state.getGroupId(), state.getEntityId(), state.getUserId());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(state), TTL);

            String indexKey = indexKey(state.getGroupId(), state.getEntityId());
            redisTemplate.opsForSet().add(indexKey, String.valueOf(state.getUserId()));
            redisTemplate.expire(indexKey, TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public DraftEditState find(Long groupId, Long entityId, Long userId) {
        try {
            String value = redisTemplate.opsForValue().get(draftKey(groupId, entityId, userId));
            return value == null ? null : objectMapper.readValue(value, DraftEditState.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<String> findEditingUsers(Long groupId, Long entityId) {
        return redisTemplate.opsForSet().members(indexKey(groupId, entityId));
    }

    public void delete(Long groupId, Long entityId, Long userId) {
        redisTemplate.delete(draftKey(groupId, entityId, userId));
        redisTemplate.opsForSet().remove(indexKey(groupId, entityId), String.valueOf(userId));
    }

    private String draftKey(Long groupId, Long entityId, Long userId) {
        return "draft:%d:%d:%d".formatted(groupId, entityId, userId);
    }

    private String indexKey(Long groupId, Long entityId) {
        return "draft-index:%d:%d".formatted(groupId, entityId);
    }
}