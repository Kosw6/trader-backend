package com.example.trader.service.cache;

import com.example.trader.entity.CacheInvalidationStatus;
import com.example.trader.entity.CacheInvalidationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationRetrySchedulerTest {

    @Mock CacheInvalidationRetryService retryService;
    @Mock NodeQueryCacheInvalidator cacheInvalidator;

    private CacheInvalidationRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CacheInvalidationRetryScheduler(retryService, cacheInvalidator);
    }

    @Test
    @DisplayName("재시도 무효화 성공 시 읽은 generation으로만 작업을 완료")
    void completes_successful_retry() {
        CacheInvalidationTask task = task();
        when(retryService.findDue(any(LocalDateTime.class))).thenReturn(List.of(task));
        when(retryService.completeIfOwned(task)).thenReturn(true);

        scheduler.retryPending();

        verify(cacheInvalidator).invalidate(10L);
        verify(retryService).completeIfOwned(task);
    }

    @Test
    @DisplayName("재시도 무효화 실패 시 읽은 generation으로 실패를 기록")
    void records_failed_retry() {
        CacheInvalidationTask task = task();
        IllegalStateException failure = new IllegalStateException("Redis 장애");
        when(retryService.findDue(any(LocalDateTime.class))).thenReturn(List.of(task));
        doThrow(failure).when(cacheInvalidator).invalidate(10L);

        scheduler.retryPending();

        verify(retryService).recordRetryFailure(eq(task), eq(failure), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("한 작업의 상태 기록 실패가 다음 graph 재시도를 막지 않음")
    void continues_after_one_task_cannot_record_state() {
        CacheInvalidationTask first = task();
        CacheInvalidationTask second = CacheInvalidationTask.builder()
                .graphId(20L)
                .generation("generation-2")
                .status(CacheInvalidationStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        IllegalStateException invalidationFailure = new IllegalStateException("Redis 장애");
        when(retryService.findDue(any(LocalDateTime.class))).thenReturn(List.of(first, second));
        doThrow(invalidationFailure).when(cacheInvalidator).invalidate(10L);
        doThrow(new IllegalStateException("상태 기록 실패"))
                .when(retryService)
                .recordRetryFailure(eq(first), eq(invalidationFailure), any(LocalDateTime.class));

        scheduler.retryPending();

        verify(cacheInvalidator).invalidate(20L);
        verify(retryService).completeIfOwned(second);
    }

    private CacheInvalidationTask task() {
        return CacheInvalidationTask.builder()
                .graphId(10L)
                .generation("generation-1")
                .status(CacheInvalidationStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
