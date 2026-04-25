package com.example.trader.ws.raw.edit;

import com.example.trader.dto.canvas.EditSessionDto;
import com.example.trader.dto.canvas.VersionHintDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 노드 편집 세션 및 버전 힌트 관리.
 *
 * Redis 사용 가능 시 → Redis 기반
 * Redis 불가 시       → 인메모리 ConcurrentHashMap 폴백
 *                       (버전 힌트는 미지원 → null 반환 → 호출자 DB fallback)
 */
@Slf4j
@Service
public class NodeEditSessionService {

    private final StringRedisTemplate redis;
    private final ObjectMapper         objectMapper;
    private final boolean              redisEnabled;

    // ── 인메모리 폴백 ────────────────────────────────────────────────────────
    /** editSessionKey → EditSessionDto JSON */
    private final ConcurrentHashMap<String, String> localSessions = new ConcurrentHashMap<>();

    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration HINT_TTL    = Duration.ofHours(1);
    private final AtomicBoolean warnLogged = new AtomicBoolean(false);

    @Autowired
    public NodeEditSessionService(StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper) {
        this.redis        = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redisEnabled = (stringRedisTemplate != null) && isRedisUp(stringRedisTemplate);
        if (!redisEnabled) {
            log.warn("[EditSession] Redis unavailable → 인메모리 세션 저장소로 전환합니다.");
        }
    }

    private static boolean isRedisUp(StringRedisTemplate tpl) {
        try {
            tpl.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── 키 생성 ──────────────────────────────────────────────────────────────

    private String editSessionKey(Long teamId, Long graphId, Long nodeId, Long userId) {
        return "canvas:editing:" + teamId + ":" + graphId + ":" + nodeId + ":" + userId;
    }

    private String versionHintKey(Long teamId, Long graphId, Long nodeId, int version) {
        return "canvas:version-hint:" + teamId + ":" + graphId + ":" + nodeId + ":" + version;
    }

    // ── 편집 세션 ─────────────────────────────────────────────────────────────

    public void startEditSession(Long teamId, Long graphId, Long nodeId, Long userId,
                                 int baseVersion, List<String> fields) {
        String key = editSessionKey(teamId, graphId, nodeId, userId);
        try {
            String json = objectMapper.writeValueAsString(new EditSessionDto(baseVersion, fields));
            if (redisEnabled) {
                redis.opsForValue().set(key, json, SESSION_TTL);
            } else {
                localSessions.put(key, json);
            }
        } catch (Exception e) {
            warnOnce(e);
        }
    }

    public void endEditSession(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = editSessionKey(teamId, graphId, nodeId, userId);
        try {
            if (redisEnabled) redis.delete(key);
            else localSessions.remove(key);
        } catch (Exception e) {
            warnOnce(e);
            localSessions.remove(key);   // Redis 실패 시도 로컬 정리
        }
    }

    public Optional<EditSessionDto> getEditSession(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = editSessionKey(teamId, graphId, nodeId, userId);
        try {
            String json = redisEnabled
                    ? redis.opsForValue().get(key)
                    : localSessions.get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, EditSessionDto.class));
        } catch (Exception e) {
            warnOnce(e);
            return Optional.empty();
        }
    }

    // ── 버전 힌트 ─────────────────────────────────────────────────────────────
    // 버전 힌트는 성능 최적화 목적 → Redis 없으면 null 반환 → 호출자 DB fallback

    public void saveVersionHint(Long teamId, Long graphId, Long nodeId,
                                int version, List<String> changedFields, Long changedBy) {
        if (!redisEnabled) return;   // 힌트 저장 스킵 → DB fallback으로 동작

        String key = versionHintKey(teamId, graphId, nodeId, version);
        try {
            VersionHintDto dto  = new VersionHintDto(version, changedFields, changedBy, System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(dto);
            redis.opsForValue().set(key, json, HINT_TTL);
        } catch (Exception e) {
            warnOnce(e);
        }
    }

    public Map<Integer, List<String>> getVersionHints(Long teamId, Long graphId, Long nodeId,
                                                      int fromVersion, int toVersion) {
        if (!redisEnabled) return null;   // null → 호출자 DB fallback

        Map<Integer, List<String>> hints = new LinkedHashMap<>();
        for (int v = fromVersion + 1; v <= toVersion; v++) {
            try {
                String json = redis.opsForValue().get(versionHintKey(teamId, graphId, nodeId, v));
                if (json == null) {
                    log.debug("[EditSession] Hint missing nodeId={} v={} → DB fallback", nodeId, v);
                    return null;
                }
                VersionHintDto dto = objectMapper.readValue(json, VersionHintDto.class);
                hints.put(v, dto.changedFields());
            } catch (JsonProcessingException e) {
                log.warn("[EditSession] Hint deserialize failed nodeId={} v={}: {}", nodeId, v, e.getMessage());
                return null;
            } catch (Exception e) {
                warnOnce(e);
                return null;
            }
        }
        return hints;
    }

    public boolean hasCompleteChain(Long teamId, Long graphId, Long nodeId, int fromVersion, int toVersion) {
        return getVersionHints(teamId, graphId, nodeId, fromVersion, toVersion) != null;
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    private void warnOnce(Exception e) {
        if (warnLogged.compareAndSet(false, true)) {
            log.warn("[EditSession] Redis 오류 → 폴백 동작 중: {}", e.toString());
        }
    }
}
