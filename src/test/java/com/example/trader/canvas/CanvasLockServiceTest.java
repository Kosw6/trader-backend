package com.example.trader.canvas;

import com.example.trader.ws.raw.lock.CanvasLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CanvasLockService 단위 테스트.
 *
 * 검증 목표:
 *  - tryAcquire: SETNX 성공/실패 분기, 유저 인덱스 등록 여부
 *  - release: 본인/타인 락 분기, Redis 키 삭제 여부
 *  - releaseAllByUser: 유저 인덱스 순회 해제, 빈 경우 처리
 *  - keepAlive: 본인/타인 분기, expire 호출 여부
 */
@ExtendWith(MockitoExtension.class)
class CanvasLockServiceTest {

    @Mock StringRedisTemplate             stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SetOperations<String, String>   setOps;

    CanvasLockService lockService;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 2L;
    static final Long NODE_ID  = 100L;
    static final Long USER_ID  = 10L;
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        lockService = new CanvasLockService(stringRedisTemplate);
    }

    // ── tryAcquire ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tryAcquire 성공 → true 반환, 유저 인덱스에 nodeId 추가")
    void try_acquire_success() {
        String lockKey     = lockKey(NODE_ID);
        String userLockKey = userLocksKey(USER_ID);

        when(valueOps.setIfAbsent(eq(lockKey), eq(String.valueOf(USER_ID)), eq(LOCK_TTL)))
                .thenReturn(true);

        boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isTrue();
        // 유저 인덱스에 nodeId 등록 확인
        verify(setOps).add(eq(userLockKey), eq(String.valueOf(NODE_ID)));
    }

    @Test
    @DisplayName("tryAcquire 실패 (이미 잠긴 노드) → false 반환, 유저 인덱스 미등록")
    void try_acquire_fail_already_locked() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isFalse();
        // 유저 인덱스 추가 없어야 함
        verify(setOps, never()).add(anyString(), anyString());
    }

    // ── getLockHolder ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLockHolder → Redis 값 있으면 userId 반환")
    void get_lock_holder_returns_user_id() {
        when(valueOps.get(lockKey(NODE_ID))).thenReturn(String.valueOf(USER_ID));

        Optional<Long> holder = lockService.getLockHolder(TEAM_ID, GRAPH_ID, NODE_ID);

        assertThat(holder).contains(USER_ID);
    }

    @Test
    @DisplayName("getLockHolder → Redis 값 없으면 empty 반환")
    void get_lock_holder_returns_empty_when_no_lock() {
        when(valueOps.get(lockKey(NODE_ID))).thenReturn(null);

        Optional<Long> holder = lockService.getLockHolder(TEAM_ID, GRAPH_ID, NODE_ID);

        assertThat(holder).isEmpty();
    }

    // ── release ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("release 본인 락 → true 반환, Redis 키 삭제, 유저 인덱스 제거")
    void release_own_lock_success() {
        when(valueOps.get(lockKey(NODE_ID))).thenReturn(String.valueOf(USER_ID));

        boolean result = lockService.release(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isTrue();
        verify(stringRedisTemplate).delete(lockKey(NODE_ID));
        verify(setOps).remove(eq(userLocksKey(USER_ID)), eq(String.valueOf(NODE_ID)));
    }

    @Test
    @DisplayName("release 타인 락 → false 반환, Redis 키 삭제 없음")
    void release_others_lock_returns_false() {
        Long otherUserId = 99L;
        when(valueOps.get(lockKey(NODE_ID))).thenReturn(String.valueOf(otherUserId));

        boolean result = lockService.release(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isFalse();
        verify(stringRedisTemplate, never()).delete(anyString());
        verify(setOps, never()).remove(anyString(), anyString());
    }

    // ── releaseAllByUser ──────────────────────────────────────────────────────

    @Test
    @DisplayName("releaseAllByUser → 유저 인덱스의 모든 nodeId 해제, 해제된 목록 반환")
    void release_all_by_user_returns_released_node_ids() {
        Long nodeId2 = 200L;
        when(setOps.members(userLocksKey(USER_ID)))
                .thenReturn(Set.of(String.valueOf(NODE_ID), String.valueOf(nodeId2)));
        when(valueOps.get(lockKey(NODE_ID))).thenReturn(String.valueOf(USER_ID));
        when(valueOps.get(lockKey(nodeId2))).thenReturn(String.valueOf(USER_ID));

        List<Long> released = lockService.releaseAllByUser(TEAM_ID, GRAPH_ID, USER_ID);

        assertThat(released).containsExactlyInAnyOrder(NODE_ID, nodeId2);
        verify(stringRedisTemplate).delete(lockKey(NODE_ID));
        verify(stringRedisTemplate).delete(lockKey(nodeId2));
        // 유저 인덱스 키 삭제 확인
        verify(stringRedisTemplate).delete(userLocksKey(USER_ID));
    }

    @Test
    @DisplayName("releaseAllByUser → 유저 인덱스 비어있으면 빈 리스트, Redis 조회 없음")
    void release_all_by_user_empty_index() {
        when(setOps.members(userLocksKey(USER_ID))).thenReturn(Set.of());

        List<Long> released = lockService.releaseAllByUser(TEAM_ID, GRAPH_ID, USER_ID);

        assertThat(released).isEmpty();
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("releaseAllByUser → 인덱스에 있어도 실제 lock holder 아니면 해제 안 함")
    void release_all_by_user_skips_if_not_holder() {
        // 인덱스에는 nodeId가 있지만 실제 락 점유자는 다른 유저
        when(setOps.members(userLocksKey(USER_ID)))
                .thenReturn(Set.of(String.valueOf(NODE_ID)));
        when(valueOps.get(lockKey(NODE_ID))).thenReturn("999"); // 다른 유저

        List<Long> released = lockService.releaseAllByUser(TEAM_ID, GRAPH_ID, USER_ID);

        assertThat(released).isEmpty();
        verify(stringRedisTemplate, never()).delete(lockKey(NODE_ID));
    }

    // ── keepAlive ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("keepAlive 본인 락 → true 반환, expire 호출")
    void keep_alive_own_lock() {
        when(valueOps.get(lockKey(NODE_ID))).thenReturn(String.valueOf(USER_ID));
        when(stringRedisTemplate.expire(lockKey(NODE_ID), LOCK_TTL)).thenReturn(true);

        boolean result = lockService.keepAlive(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isTrue();
        verify(stringRedisTemplate).expire(lockKey(NODE_ID), LOCK_TTL);
    }

    @Test
    @DisplayName("keepAlive 타인 락 → false 반환, expire 호출 없음")
    void keep_alive_others_lock_returns_false() {
        when(valueOps.get(lockKey(NODE_ID))).thenReturn("999");

        boolean result = lockService.keepAlive(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isFalse();
        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    // ── 키 포맷 헬퍼 (CanvasLockService 내부 키와 동일하게 유지) ──────────────

    private String lockKey(Long nodeId) {
        return "canvas:lock:" + TEAM_ID + ":" + GRAPH_ID + ":" + nodeId;
    }

    private String userLocksKey(Long userId) {
        return "canvas:user-locks:" + TEAM_ID + ":" + GRAPH_ID + ":" + userId;
    }
}
