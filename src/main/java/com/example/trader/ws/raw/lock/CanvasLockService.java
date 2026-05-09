package com.example.trader.ws.raw.lock;

import com.example.trader.entity.CanvasLock;
import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.repository.CanvasLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasLockService {

    private final StringRedisTemplate redis;
    private final RedisHealthState redisHealthState;
    private final CanvasLockRepository canvasLockRepository;

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    public long getLockTtlMs() {
        return LOCK_TTL.toMillis();
    }

    private final AtomicBoolean warnLogged = new AtomicBoolean(false);

    private static final String ACQUIRE_SCRIPT = """
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[3]) then
                redis.call('SADD', KEYS[2], ARGV[2])
                redis.call('EXPIRE', KEYS[2], ARGV[3])
                return 1
            else
                return 0
            end
            """;

    private static final String UNLOCK_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """;

    private static final String RENEW_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('EXPIRE', KEYS[1], ARGV[2])
            else
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> ACQUIRE_REDIS_SCRIPT =
            new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private static final DefaultRedisScript<Long> RENEW_REDIS_SCRIPT =
            new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);

    private String lockKey(Long teamId, Long graphId, Long nodeId) {
        return "canvas:lock:" + teamId + ":" + graphId + ":" + nodeId;
    }

    private String userLocksKey(Long teamId, Long graphId, Long userId) {
        return "canvas:user-locks:" + teamId + ":" + graphId + ":" + userId;
    }

    public boolean tryAcquire(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = lockKey(teamId, graphId, nodeId);
        String userKey = userLocksKey(teamId, graphId, userId);

        String uid = String.valueOf(userId);
        String nid = String.valueOf(nodeId);
        String ttlSeconds = String.valueOf(LOCK_TTL.toSeconds());

        if (redisHealthState.isAvailable()) {
            try {
                Long result = redis.execute(
                        ACQUIRE_REDIS_SCRIPT,
                        List.of(key, userKey),
                        uid,
                        nid,
                        ttlSeconds
                );

                boolean acquired = result != null && result > 0;
                if (acquired) {
                    log.info("[CanvasLock-REDIS] 획득 성공. key={}, userId={}", key, userId);
                } else {
                    log.info("[CanvasLock-REDIS] 획득 실패(이미 점유). key={}, userId={}", key, userId);
                }
                return acquired;
            } catch (Exception e) {
                warnOnce(e);
                redisHealthState.markDown();
            }
        }

        return tryAcquireFromDb(teamId, graphId, nodeId, userId);
    }

    public Optional<Long> getLockHolder(Long teamId, Long graphId, Long nodeId) {
        String key = lockKey(teamId, graphId, nodeId);

        if (redisHealthState.isAvailable()) {
            try {
                String val = redis.opsForValue().get(key);
                return parseUserId(val);
            } catch (Exception e) {
                warnOnce(e);
                redisHealthState.markDown();
            }
        }

        return canvasLockRepository.findByTeamIdAndGraphIdAndNodeId(teamId, graphId, nodeId)
                .filter(lock -> !lock.isExpired())
                .map(CanvasLock::getUserId);
    }

    public boolean release(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = lockKey(teamId, graphId, nodeId);
        String userKey = userLocksKey(teamId, graphId, userId);

        String uid = String.valueOf(userId);
        String nid = String.valueOf(nodeId);

        if (redisHealthState.isAvailable()) {
            try {
                Long result = redis.execute(
                        UNLOCK_REDIS_SCRIPT,
                        List.of(key),
                        uid
                );

                if (result != null && result > 0) {
                    redis.opsForSet().remove(userKey, nid);
                    log.info("[CanvasLock-REDIS] 해제 성공. key={}, userId={}", key, userId);
                    return true;
                }

                log.info("[CanvasLock-REDIS] 해제 실패(락 없음 또는 소유자 불일치). key={}, userId={}", key, userId);
                return false;
            } catch (Exception e) {
                warnOnce(e);
                redisHealthState.markDown();
            }
        }

        int deleted = canvasLockRepository.deleteByNodeAndUser(teamId, graphId, nodeId, userId);
        if (deleted > 0) {
            log.info("[CanvasLock-DB] 해제 성공. teamId={}, graphId={}, nodeId={}, userId={}",
                    teamId, graphId, nodeId, userId);
        } else {
            log.info("[CanvasLock-DB] 해제 실패(레코드 없음). teamId={}, graphId={}, nodeId={}, userId={}",
                    teamId, graphId, nodeId, userId);
        }
        return deleted > 0;
    }

    public List<Long> releaseAllByUser(Long teamId, Long graphId, Long userId) {
        String userKey = userLocksKey(teamId, graphId, userId);
        String uid = String.valueOf(userId);

        if (redisHealthState.isAvailable()) {
            try {
                Set<String> nodeIds = redis.opsForSet().members(userKey);
                if (nodeIds == null || nodeIds.isEmpty()) return List.of();

                List<Long> released = new ArrayList<>();

                for (String nid : nodeIds) {
                    try {
                        Long nodeId = Long.parseLong(nid);
                        String key = lockKey(teamId, graphId, nodeId);

                        Long result = redis.execute(
                                UNLOCK_REDIS_SCRIPT,
                                List.of(key),
                                uid
                        );

                        if (result != null && result > 0) {
                            released.add(nodeId);
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }

                redis.delete(userKey);
                log.info("[CanvasLock-REDIS] 연결 종료 일괄 해제. userId={}, releasedNodes={}",
                        userId, released);
                return released;
            } catch (Exception e) {
                warnOnce(e);
                redisHealthState.markDown();
            }
        }

        List<CanvasLock> locks =
                canvasLockRepository.findAllByTeamIdAndGraphIdAndUserId(teamId, graphId, userId);

        if (locks.isEmpty()) return List.of();

        List<Long> released = locks.stream()
                .map(CanvasLock::getNodeId)
                .toList();

        canvasLockRepository.deleteAllByUser(teamId, graphId, userId);
        log.info("[CanvasLock-DB] 연결 종료 일괄 해제. userId={}, releasedNodes={}",
                userId, released);
        return released;
    }

    public boolean keepAlive(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = lockKey(teamId, graphId, nodeId);
        String userKey = userLocksKey(teamId, graphId, userId);

        String uid = String.valueOf(userId);

        if (redisHealthState.isAvailable()) {
            try {
                Long result = redis.execute(
                        RENEW_REDIS_SCRIPT,
                        List.of(key),
                        uid,
                        String.valueOf(LOCK_TTL.toSeconds())
                );

                boolean renewed = result != null && result > 0;

                if (renewed) {
                    redis.expire(userKey, LOCK_TTL);
                    log.info("[CanvasLock-REDIS] keepAlive 성공. key={}, userId={}", key, userId);
                } else {
                    log.info("[CanvasLock-REDIS] keepAlive 실패(락 없음 또는 소유자 불일치). key={}, userId={}", key, userId);
                }

                return renewed;
            } catch (Exception e) {
                warnOnce(e);
                redisHealthState.markDown();
            }
        }

        return canvasLockRepository.findByTeamIdAndGraphIdAndNodeId(teamId, graphId, nodeId)
                .filter(lock -> lock.getUserId().equals(userId))
                .map(lock -> {
                    lock.refresh(LOCK_TTL);
                    canvasLockRepository.save(lock);
                    log.info("[CanvasLock-DB] keepAlive 성공. teamId={}, graphId={}, nodeId={}, userId={}",
                            teamId, graphId, nodeId, userId);
                    return true;
                })
                .orElseGet(() -> {
                    log.info("[CanvasLock-DB] keepAlive 실패(락 없음). teamId={}, graphId={}, nodeId={}, userId={}",
                            teamId, graphId, nodeId, userId);
                    return false;
                });
    }

    private boolean tryAcquireFromDb(Long teamId, Long graphId, Long nodeId, Long userId) {
        canvasLockRepository.deleteExpiredByNode(teamId, graphId, nodeId, LocalDateTime.now());

        try {
            canvasLockRepository.saveAndFlush(CanvasLock.builder()
                    .teamId(teamId)
                    .graphId(graphId)
                    .nodeId(nodeId)
                    .userId(userId)
                    .expiresAt(LocalDateTime.now().plus(LOCK_TTL))
                    .build());

            log.info("[CanvasLock] DB fallback 락 획득. teamId={}, graphId={}, nodeId={}, userId={}",
                    teamId, graphId, nodeId, userId);

            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("[CanvasLock] DB fallback 락 획득 실패. 이미 점유 중. teamId={}, graphId={}, nodeId={}",
                    teamId, graphId, nodeId);
            return false;
        }
    }

    private Optional<Long> parseUserId(String val) {
        if (val == null) return Optional.empty();

        try {
            return Optional.of(Long.parseLong(val));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void warnOnce(Exception e) {
        if (warnLogged.compareAndSet(false, true)) {
            log.warn("[CanvasLock] Redis 오류 → DB 락 저장소로 전환합니다: {}", e.toString());
        }
    }
}