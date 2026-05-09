package com.example.trader.canvas;

import com.example.trader.entity.CanvasLock;
import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.repository.CanvasLockRepository;
import com.example.trader.ws.raw.lock.CanvasLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시나리오 2 · 3 — DB 락 만료·재획득 및 Redis 복구 후 경로 전환 통합 테스트
 *
 * DB  : H2 인메모리 (Docker 불필요, JPQL 기반 쿼리 그대로 동작)
 * Redis: 로컬 Redis (localhost:6379) — Docker Desktop 없이 실행 가능
 *        → Redis 연결 실패 시 S3 테스트만 영향, S2는 Redis DOWN 경로만 검증하므로 무관
 *
 * [S2] 만료 DB 락 삭제 후 재획득 / 유효 DB 락 유지 시 획득 실패
 *       getLockHolder — 만료된 락 empty, 유효한 락 userId 반환
 * [S3] Redis DOWN → DB 경로 / Redis UP → Redis 경로 / DOWN→UP 자연 전환
 */
@SpringBootTest(properties = {
        // ── DB: H2 인메모리 (PostgreSQL 모드) ────────────────────────────
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1",
        // ── Redis: 로컬 (기본 설정 유지) ─────────────────────────────────
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.timeout=500ms",
        // ── 불필요한 인프라 비활성화 ──────────────────────────────────────
        "app.mode=single",
        "realtime.kafka.enabled=false",
        "realtime.redis.pubsub-enabled=false",
        "realtime.grpc.enabled=false",
        "realtime.http.enabled=false",
        "realtime.outbox.retry-enabled=false",
        // ── OAuth2 더미 값 (SecurityConfig 빈 생성용) ─────────────────────
        "spring.security.oauth2.client.registration.google.client-id=dummy",
        "spring.security.oauth2.client.registration.google.client-secret=dummy",
        "spring.security.oauth2.client.registration.kakao.client-id=dummy"
})
class CanvasLockFallbackIntegrationTest {

    @Autowired CanvasLockService      lockService;
    @Autowired CanvasLockRepository   canvasLockRepository;
    @Autowired RedisHealthState       redisHealthState;
    @Autowired StringRedisTemplate    stringRedisTemplate;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 1L;
    static final Long NODE_ID  = 1L;
    static final Long USER_A   = 10L;
    static final Long USER_B   = 20L;

    @BeforeEach
    void cleanUp() {
        // DB 락 전체 삭제
        canvasLockRepository.deleteAll();

        // RedisHealthState 복구
        redisHealthState.markUp();

        // Redis 락 키 삭제 (Redis가 살아있을 때만 실행)
        if (redisHealthState.isAvailable()) {
            try {
                Set<String> keys = stringRedisTemplate.keys("canvas:lock:" + TEAM_ID + ":" + GRAPH_ID + ":*");
                if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
                Set<String> userKeys = stringRedisTemplate.keys("canvas:user-locks:" + TEAM_ID + ":" + GRAPH_ID + ":*");
                if (userKeys != null && !userKeys.isEmpty()) stringRedisTemplate.delete(userKeys);
            } catch (Exception ignored) {
                // Redis 미실행 환경에서는 무시
            }
        }
    }

    /**
     * RedisHealthState.FAILURE_THRESHOLD = 3
     * markDown() 3회 → available = false
     */
    void forceRedisDown() {
        redisHealthState.markDown();
        redisHealthState.markDown();
        redisHealthState.markDown();
        assertThat(redisHealthState.isAvailable()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 시나리오 2 — DB 락 만료 및 재획득 (H2, Redis 불필요)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("S2 — DB 락 만료 및 재획득")
    class S2_DbLockExpiry {

        @Test
        @DisplayName("만료된 DB 락 삭제 후 userB 재획득 성공")
        void expired_lock_deleted_then_userB_acquires() {
            forceRedisDown();

            // userA 락을 이미 만료된 상태로 직접 저장
            canvasLockRepository.saveAndFlush(CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_A)
                    .expiresAt(LocalDateTime.now().minusSeconds(10))  // 이미 만료
                    .build());

            // userB tryAcquire → deleteExpiredByNode(userA 락 삭제) → INSERT 성공
            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_B);

            assertThat(result).isTrue();

            // DB에 userB 락만 남아있어야 함
            Optional<CanvasLock> stored =
                    canvasLockRepository.findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID);
            assertThat(stored).isPresent();
            assertThat(stored.get().getUserId()).isEqualTo(USER_B);
        }

        @Test
        @DisplayName("유효한 DB 락이 있으면 userB 획득 실패 (UNIQUE 제약)")
        void active_lock_exists_then_userB_denied() {
            forceRedisDown();

            // userA 락 — 아직 유효
            canvasLockRepository.saveAndFlush(CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_A)
                    .expiresAt(LocalDateTime.now().plusSeconds(30))
                    .build());

            // userB tryAcquire → deleteExpiredByNode 0건 → INSERT → UNIQUE 위반
            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_B);

            assertThat(result).isFalse();

            // userA 락 유지 확인
            Optional<CanvasLock> stored =
                    canvasLockRepository.findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID);
            assertThat(stored).isPresent();
            assertThat(stored.get().getUserId()).isEqualTo(USER_A);
        }

        @Test
        @DisplayName("만료된 DB 락 → getLockHolder empty 반환")
        void expired_lock_not_returned_by_getLockHolder() {
            forceRedisDown();

            canvasLockRepository.saveAndFlush(CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_A)
                    .expiresAt(LocalDateTime.now().minusSeconds(1))   // 만료
                    .build());

            Optional<Long> holder = lockService.getLockHolder(TEAM_ID, GRAPH_ID, NODE_ID);

            // isExpired() == true → filter 제거 → empty
            assertThat(holder).isEmpty();
        }

        @Test
        @DisplayName("유효한 DB 락 → getLockHolder userA 반환")
        void active_lock_returns_holder() {
            forceRedisDown();

            canvasLockRepository.saveAndFlush(CanvasLock.builder()
                    .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID)
                    .userId(USER_A)
                    .expiresAt(LocalDateTime.now().plusSeconds(30))
                    .build());

            Optional<Long> holder = lockService.getLockHolder(TEAM_ID, GRAPH_ID, NODE_ID);

            assertThat(holder).contains(USER_A);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 시나리오 3 — Redis 복구 후 락 경로 전환 (로컬 Redis 필요)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("S3 — Redis 복구 후 락 경로 전환")
    class S3_RedisRecovery {

        @Test
        @DisplayName("Redis DOWN → DB 경로 사용, Redis 키 저장 없음")
        void redis_down_uses_db_path() {
            forceRedisDown();

            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_A);

            assertThat(result).isTrue();

            // DB에 락 저장됨
            assertThat(canvasLockRepository
                    .findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID))
                    .isPresent();

            // Redis에는 키 없음 (DOWN 상태였으므로)
            if (isRedisActuallyRunning()) {
                String redisKey = "canvas:lock:" + TEAM_ID + ":" + GRAPH_ID + ":" + NODE_ID;
                assertThat(stringRedisTemplate.hasKey(redisKey)).isFalse();
            }
        }

        @Test
        @DisplayName("Redis UP → Redis 경로 사용, DB INSERT 없음")
        void redis_up_uses_redis_path() {
            // Redis가 실제로 실행 중일 때만 의미있는 테스트
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    isRedisActuallyRunning(),
                    "로컬 Redis가 실행 중이지 않으므로 스킵"
            );

            assertThat(redisHealthState.isAvailable()).isTrue();

            boolean result = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_A);

            assertThat(result).isTrue();

            // Redis에 락 키 존재
            String redisKey = "canvas:lock:" + TEAM_ID + ":" + GRAPH_ID + ":" + NODE_ID;
            assertThat(stringRedisTemplate.hasKey(redisKey)).isTrue();
            assertThat(stringRedisTemplate.opsForValue().get(redisKey))
                    .isEqualTo(String.valueOf(USER_A));

            // DB에는 락 없음
            assertThat(canvasLockRepository
                    .findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID))
                    .isEmpty();
        }

        @Test
        @DisplayName("Redis DOWN 이후 markUp → Redis 경로로 자연 전환")
        void switches_from_db_to_redis_after_recovery() {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    isRedisActuallyRunning(),
                    "로컬 Redis가 실행 중이지 않으므로 스킵"
            );

            // ── Phase 1: Redis DOWN → DB 경로 ──────────────────────────────
            forceRedisDown();

            boolean dbResult = lockService.tryAcquire(TEAM_ID, GRAPH_ID, NODE_ID, USER_A);
            assertThat(dbResult).isTrue();
            assertThat(canvasLockRepository
                    .findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, NODE_ID))
                    .isPresent();

            // ── Phase 2: Redis 복구 → Redis 경로 전환 ─────────────────────
            Long node2 = 2L;
            canvasLockRepository.deleteAll();
            redisHealthState.markUp();

            assertThat(redisHealthState.isAvailable()).isTrue();

            boolean redisResult = lockService.tryAcquire(TEAM_ID, GRAPH_ID, node2, USER_A);
            assertThat(redisResult).isTrue();

            // Redis에 node2 락 존재
            String redisKey2 = "canvas:lock:" + TEAM_ID + ":" + GRAPH_ID + ":" + node2;
            assertThat(stringRedisTemplate.hasKey(redisKey2)).isTrue();

            // DB에는 node2 락 없음 (Redis 경로 사용)
            assertThat(canvasLockRepository
                    .findByTeamIdAndGraphIdAndNodeId(TEAM_ID, GRAPH_ID, node2))
                    .isEmpty();
        }

        /** 실제 Redis 연결 가능 여부 확인 */
        private boolean isRedisActuallyRunning() {
            try {
                stringRedisTemplate.getConnectionFactory()
                        .getConnection()
                        .ping();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
