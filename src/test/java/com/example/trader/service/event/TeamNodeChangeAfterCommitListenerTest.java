package com.example.trader.service.event;

import com.example.trader.service.cache.CacheInvalidationRetryService;
import com.example.trader.service.cache.NodeQueryCacheInvalidator;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamNodeChangeAfterCommitListenerTest {

    @Mock NodeEditSessionService editSessionService;
    @Mock NodeQueryCacheInvalidator cacheInvalidator;
    @Mock CacheInvalidationRetryService retryService;

    private TeamNodeChangeAfterCommitListener listener;

    @BeforeEach
    void setUp() {
        listener = new TeamNodeChangeAfterCommitListener(
                editSessionService,
                cacheInvalidator,
                retryService
        );
    }

    @Test
    @DisplayName("커밋 후 확정 정보로 힌트, 세션 종료, 두 캐시 무효화를 실행")
    void runs_all_after_commit_tasks() {
        TeamNodeChangeCommittedEvent event = event();

        listener.handle(event);

        InOrder order = inOrder(editSessionService, cacheInvalidator);
        order.verify(editSessionService)
                .saveVersionHint(1L, 2L, 3L, 4, List.of("subject"), 5L);
        order.verify(editSessionService).endEditSession(1L, 2L, 3L, 5L);
        order.verify(cacheInvalidator).invalidate(2L);
    }

    @Test
    @DisplayName("힌트 저장과 세션 종료가 실패해도 두 캐시 무효화를 계속 실행")
    void isolates_each_after_commit_failure() {
        TeamNodeChangeCommittedEvent event = event();
        doThrow(new IllegalStateException("힌트 저장 실패"))
                .when(editSessionService)
                .saveVersionHint(1L, 2L, 3L, 4, List.of("subject"), 5L);
        doThrow(new IllegalStateException("세션 종료 실패"))
                .when(editSessionService)
                .endEditSession(1L, 2L, 3L, 5L);

        listener.handle(event);

        verify(cacheInvalidator).invalidate(2L);
    }

    @Test
    @DisplayName("즉시 캐시 무효화가 실패하면 내구성 재시도 작업을 기록")
    void records_durable_retry_when_cache_invalidation_fails() {
        TeamNodeChangeCommittedEvent event = event();
        IllegalStateException failure = new IllegalStateException("Redis 삭제 실패");
        doThrow(failure).when(cacheInvalidator).invalidate(2L);

        listener.handle(event);

        verify(retryService).recordInitialFailure(2L, failure);
    }

    private TeamNodeChangeCommittedEvent event() {
        return new TeamNodeChangeCommittedEvent(1L, 2L, 3L, 5L, 4, List.of("subject"));
    }
}
