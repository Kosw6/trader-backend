package com.example.trader.ws.raw.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 노드 이동 독점 락 관리.
 *
 * Redis 사용 가능 시 → Redis 기반 (분산 환경 대응)
 * Redis 불가 시       → 인메모리 ConcurrentHashMap 폴백 (단일 인스턴스 환경)
 */
@Slf4j
@Service
public class CanvasLockService {

    private final StringRedisTemplate redis;
    private final boolean redisEnabled;

    // ── 인메모리 폴백 저장소 ──────────────────────────────────────────────────
    /** lockKey → userId */
    private final ConcurrentHashMap<String, String> localLocks    = new ConcurrentHashMap<>();
    /** userLocksKey → Set<nodeId> */
    private final ConcurrentHashMap<String, Set<String>> localUserLocks = new ConcurrentHashMap<>();

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private final AtomicBoolean warnLogged = new AtomicBoolean(false);

    @Autowired
    public CanvasLockService(StringRedisTemplate stringRedisTemplate) {
        this.redis = stringRedisTemplate;
        this.redisEnabled = (stringRedisTemplate != null) && isRedisUp(stringRedisTemplate);
        if (!redisEnabled) {
            log.warn("[CanvasLock] Redis unavailable → 인메모리 락 저장소로 전환합니다.");
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

    private String lockKey(Long teamId, Long graphId, Long nodeId) {
        return "canvas:lock:" + teamId + ":" + graphId + ":" + nodeId;
    }

    private String userLocksKey(Long teamId, Long graphId, Long userId) {
        return "canvas:user-locks:" + teamId + ":" + graphId + ":" + userId;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean tryAcquire(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key     = lockKey(teamId, graphId, nodeId);
        String userKey = userLocksKey(teamId, graphId, userId);
        String uid     = String.valueOf(userId);
        String nid     = String.valueOf(nodeId);

        if (redisEnabled) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(key, uid, LOCK_TTL);
                if (Boolean.TRUE.equals(ok)) {
                    redis.opsForSet().add(userKey, nid);
                    return true;
                }
                return false;
            } catch (Exception e) {
                warnOnce(e);
            }
        }

        // 인메모리 폴백
        String prev = localLocks.putIfAbsent(key, uid);
        if (prev == null) {
            localUserLocks.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet()).add(nid);
            return true;
        }
        return false;
    }

    public Optional<Long> getLockHolder(Long teamId, Long graphId, Long nodeId) {
        String key = lockKey(teamId, graphId, nodeId);

        if (redisEnabled) {
            try {
                String val = redis.opsForValue().get(key);
                return parseUserId(val);
            } catch (Exception e) {
                warnOnce(e);
            }
        }

        return parseUserId(localLocks.get(key));
    }

    public boolean release(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key     = lockKey(teamId, graphId, nodeId);
        String userKey = userLocksKey(teamId, graphId, userId);
        String uid     = String.valueOf(userId);
        String nid     = String.valueOf(nodeId);

        if (redisEnabled) {
            try {
                String holder = redis.opsForValue().get(key);
                if (!uid.equals(holder)) return false;
                redis.delete(key);
                redis.opsForSet().remove(userKey, nid);
                return true;
            } catch (Exception e) {
                warnOnce(e);
            }
        }

        // 인메모리 폴백
        boolean removed = localLocks.remove(key, uid);
        if (removed) {
            Set<String> set = localUserLocks.get(userKey);
            if (set != null) set.remove(nid);
        }
        return removed;
    }

    public List<Long> releaseAllByUser(Long teamId, Long graphId, Long userId) {
        String userKey = userLocksKey(teamId, graphId, userId);
        String uid     = String.valueOf(userId);

        if (redisEnabled) {
            try {
                Set<String> nodeIds = redis.opsForSet().members(userKey);
                if (nodeIds == null || nodeIds.isEmpty()) return List.of();

                List<Long> released = new ArrayList<>();
                for (String nid : nodeIds) {
                    try {
                        Long nodeId = Long.parseLong(nid);
                        String lockKey = lockKey(teamId, graphId, nodeId);
                        String holder  = redis.opsForValue().get(lockKey);
                        if (uid.equals(holder)) {
                            redis.delete(lockKey);
                            released.add(nodeId);
                        }
                    } catch (NumberFormatException ignore) {}
                }
                redis.delete(userKey);
                return released;
            } catch (Exception e) {
                warnOnce(e);
            }
        }

        // 인메모리 폴백
        Set<String> nodeIds = localUserLocks.remove(userKey);
        if (nodeIds == null || nodeIds.isEmpty()) return List.of();

        List<Long> released = new ArrayList<>();
        for (String nid : nodeIds) {
            try {
                Long nodeId = Long.parseLong(nid);
                String lockKey = lockKey(teamId, graphId, nodeId);
                if (localLocks.remove(lockKey, uid)) released.add(nodeId);
            } catch (NumberFormatException ignore) {}
        }
        return released;
    }

    public boolean keepAlive(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = lockKey(teamId, graphId, nodeId);
        String uid = String.valueOf(userId);

        if (redisEnabled) {
            try {
                String holder = redis.opsForValue().get(key);
                if (!uid.equals(holder)) return false;
                return Boolean.TRUE.equals(redis.expire(key, LOCK_TTL));
            } catch (Exception e) {
                warnOnce(e);
            }
        }

        // 인메모리: TTL 없이 그냥 점유 확인만
        return uid.equals(localLocks.get(key));
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    private Optional<Long> parseUserId(String val) {
        if (val == null) return Optional.empty();
        try { return Optional.of(Long.parseLong(val)); }
        catch (NumberFormatException e) { return Optional.empty(); }
    }

    private void warnOnce(Exception e) {
        if (warnLogged.compareAndSet(false, true)) {
            log.warn("[CanvasLock] Redis 오류 → 인메모리 폴백 사용: {}", e.toString());
        }
    }
}
