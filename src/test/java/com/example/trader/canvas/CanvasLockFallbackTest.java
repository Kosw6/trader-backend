package com.example.trader.canvas;

import com.example.trader.entity.CanvasLock;
import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.repository.CanvasLockRepository;
import com.example.trader.ws.raw.lock.CanvasLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 시나리오 2 — DB 락 만료 및 재획득
 * 시나리오 3 — Redis 복구 후 락 경로 전환
 *
 * 검증 목표:
 *  [S2] expiresAt 만료 후 deleteExpiredByNode → 다른 유저 재획득 가능
 *  [S2] 만료 안 된 락 존재 시 DataIntegrityViolationException → 획득 실패
 *  [S2] getLockHolder: isExpired() true → empty 반환
 *  [S3] Redis DOWN → DB 경로 사용 확인
 *  [S3] markUp() 이후 → Redis 경로로 전환, DB INSERT 없음
 */
@ExtendWith(MockitoExtension.class)
class CanvasLockFallbackTest {

    @Mock StringRedisTemplate       redis;
    @Mock RedisHealthState          redisHealthState;
    @Mock CanvasLockRepository      canvasLockRepository;

    CanvasLockService lockService;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 1L;
    static final Long NODE_ID  = 1L;
    static final Long USER_A   = 10L;
    static final Long USER_B   = 20L;

    @BeforeEach
    void setUp() {
        lockService = new CanvasLockService(redis, redisHealthState, canvasLockRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 시나리오 2 — DB 락 만료 및 재획득
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("S2 — DB 락 만료 및 재획득")
    class S2_DbLockExpiry {

        @Test
        @DisplayName("만료된 락 삭제 후 userB 재획득 성공 → deleteExpiredByNode + saveAndFlush 호출")
        void expired_lock_deleted_then_userB_acquires() {
            // Redis DOWN
            when(redisHealthState.isAvailable()).thenReturn(false);

            // deleteExpiredByNode: 만료된 userA 락 1건 삭제
            when(canvasLockRepository.deleteExpiredByNode(
                    eq(TEAM_ID), eq(GRAPH_ID), eq(NODE_ID), any(LocalDateTime.class)))
                    .thenReturn(1);

            // saveAndFlush: userB 락 INSERT 성공
            CanvasLock userBLock = CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_B)
                    .expiresAt(LocalDateTime.now().plusSeconds(5))
                    .build();
            when(canvasLockRepository.saveAndFlush(any())).thenReturn(userBLock);

            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_B);

            assertThat(result).isTrue();
            // 만료 락 정리 호출 확인
            verify(canvasLockRepository)
                    .deleteExpiredByNode(eq(TEAM_ID), eq(GRAPH_ID), eq(NODE_ID), any(LocalDateTime.class));
            // userB 락 INSERT 확인
            verify(canvasLockRepository).saveAndFlush(any(CanvasLock.class));
        }

        @Test
        @DisplayName("만료 안 된 락 존재 시 UNIQUE constraint 위반 → userB 획득 실패")
        void active_lock_exists_then_userB_denied() {
            // Redis DOWN
            when(redisHealthState.isAvailable()).thenReturn(false);

            // deleteExpiredByNode: 삭제 없음 (락 아직 유효)
            when(canvasLockRepository.deleteExpiredByNode(
                    eq(TEAM_ID), eq(GRAPH_ID), eq(NODE_ID), any(LocalDateTime.class)))
                    .thenReturn(0);

            // saveAndFlush: userA 락이 살아있어 UNIQUE constraint 위반
            when(canvasLockRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_B);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("keepAlive 미호출로 만료된 락 → getLockHolder empty 반환")
        void expired_lock_not_returned_by_getLockHolder() {
            // Redis DOWN
            when(redisHealthState.isAvailable()).thenReturn(false);

            // expiresAt이 과거인 만료 락 반환
            CanvasLock expiredLock = CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_A)
                    .expiresAt(LocalDateTime.now().minusSeconds(1))  // 이미 만료
                    .build();
            when(canvasLockRepository.findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID))
                    .thenReturn(Optional.of(expiredLock));

            Optional<Long> holder = lockService.getLockHolder(TEAM_ID, GRAPH_ID, NODE_ID);

            // isExpired() → true → 필터에서 제거 → empty
            assertThat(holder).isEmpty();
        }

        @Test
        @DisplayName("유효한 락 → getLockHolder userA 반환")
        void active_lock_returns_holder() {
            // Redis DOWN
            when(redisHealthState.isAvailable()).thenReturn(false);

            CanvasLock activeLock = CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_A)
                    .expiresAt(LocalDateTime.now().plusSeconds(5))  // 유효
                    .build();
            when(canvasLockRepository.findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID))
                    .thenReturn(Optional.of(activeLock));

            Optional<Long> holder = lockService.getLockHolder(TEAM_ID, GRAPH_ID, NODE_ID);

            assertThat(holder).contains(USER_A);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 시나리오 3 — Redis 복구 후 락 경로 전환
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("S3 — Redis 복구 후 락 경로 전환")
    class S3_RedisRecovery {

        @Test
        @DisplayName("Redis DOWN → DB 경로 사용, DB INSERT 발생")
        void redis_down_uses_db_path() {
            when(redisHealthState.isAvailable()).thenReturn(false);
            when(canvasLockRepository.deleteExpiredByNode(any(), any(), any(), any()))
                    .thenReturn(0);
            when(canvasLockRepository.saveAndFlush(any()))
                    .thenReturn(mock(CanvasLock.class));

            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_A);

            assertThat(result).isTrue();
            // DB 경로 사용 확인
            verify(canvasLockRepository).deleteExpiredByNode(any(), any(), any(), any());
            verify(canvasLockRepository).saveAndFlush(any());
            // Redis Lua 스크립트 호출 없음
            verify(redis, never()).execute(any(RedisScript.class), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Redis 복구 후 → Redis 경로 사용, DB INSERT 없음")
        void after_redis_recovery_uses_redis_path() {
            // Redis UP
            when(redisHealthState.isAvailable()).thenReturn(true);
            // Lua ACQUIRE 스크립트 성공 (1 반환)
            when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .thenReturn(1L);

            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_A);

            assertThat(result).isTrue();
            // Redis 경로 사용 확인
            verify(redis).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
            // DB 경로 전혀 사용 안 함
            verify(canvasLockRepository, never()).saveAndFlush(any());
            verify(canvasLockRepository, never()).deleteExpiredByNode(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Redis DOWN 이후 복구 → DB 경로에서 Redis 경로로 자연 전환")
        void switches_from_db_to_redis_after_recovery() {
            // Phase 1: Redis DOWN → DB 경로
            when(redisHealthState.isAvailable()).thenReturn(false);
            when(canvasLockRepository.deleteExpiredByNode(any(), any(), any(), any()))
                    .thenReturn(0);
            when(canvasLockRepository.saveAndFlush(any()))
                    .thenReturn(mock(CanvasLock.class));

            boolean dbResult = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_A);
            assertThat(dbResult).isTrue();
            verify(canvasLockRepository).saveAndFlush(any());

            // Phase 2: Redis 복구 → Redis 경로
            when(redisHealthState.isAvailable()).thenReturn(true);
            when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .thenReturn(1L);

            boolean redisResult = lockService.tryAcquire(TEAM_ID, GRAPH_ID, 2L, USER_A);
            assertThat(redisResult).isTrue();

            // Phase 2에서 DB INSERT 추가 호출 없음 (Phase 1의 1회에서 멈춤)
            verify(canvasLockRepository, times(1)).saveAndFlush(any());
            // Redis 호출 발생
            verify(redis).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
        }
    }
}
