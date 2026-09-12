package com.example.trader.service.cache;

import com.example.trader.entity.CacheInvalidationStatus;
import com.example.trader.entity.CacheInvalidationTask;
import com.example.trader.repository.CacheInvalidationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationRetryServiceTest {

    @Mock CacheInvalidationTaskRepository repository;

    private CacheInvalidationRetryService service;

    @BeforeEach
    void setUp() {
        service = new CacheInvalidationRetryService(repository);
    }

    @Test
    @DisplayName("첫 재시도 실패는 횟수를 증가시키고 5초 뒤로 예약")
    void schedules_next_retry_with_backoff() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
        CacheInvalidationTask task = task(0);
        when(repository.updateFailureIfOwned(
                eq(10L), eq("generation-1"), eq(CacheInvalidationStatus.PENDING),
                eq(1), eq(now.plusSeconds(5)), eq("Redis 장애"), eq(now)
        )).thenReturn(1);

        boolean updated = service.recordRetryFailure(
                task,
                new IllegalStateException("Redis 장애"),
                now
        );

        assertThat(updated).isTrue();
    }

    @Test
    @DisplayName("다섯 번째 재시도 실패는 FAILED로 격리하고 다음 시각을 제거")
    void quarantines_exhausted_task() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 12, 0);
        CacheInvalidationTask task = task(4);
        when(repository.updateFailureIfOwned(
                eq(10L), eq("generation-1"), eq(CacheInvalidationStatus.FAILED),
                eq(5), isNull(), eq("계속 실패"), eq(now)
        )).thenReturn(1);

        boolean updated = service.recordRetryFailure(
                task,
                new IllegalStateException("계속 실패"),
                now
        );

        assertThat(updated).isTrue();
    }

    @Test
    @DisplayName("generation이 바뀐 작업은 이전 작업자가 완료 처리하지 못함")
    void stale_generation_cannot_complete_new_task() {
        CacheInvalidationTask task = task(0);
        when(repository.deleteIfOwned(10L, "generation-1")).thenReturn(0);

        assertThat(service.completeIfOwned(task)).isFalse();

        verify(repository).deleteIfOwned(10L, "generation-1");
    }

    private CacheInvalidationTask task(int retryCount) {
        return CacheInvalidationTask.builder()
                .graphId(10L)
                .generation("generation-1")
                .status(CacheInvalidationStatus.PENDING)
                .retryCount(retryCount)
                .nextRetryAt(LocalDateTime.of(2026, 9, 12, 12, 0))
                .lastError("이전 실패")
                .createdAt(LocalDateTime.of(2026, 9, 12, 11, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 12, 11, 0))
                .build();
    }
}
