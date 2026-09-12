package com.example.trader.service.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeQueryCacheInvalidatorTest {

    @Mock CacheManager cacheManager;
    @Mock Cache pageNodes;
    @Mock Cache graphDetail;

    private NodeQueryCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        invalidator = new NodeQueryCacheInvalidator(cacheManager);
        when(cacheManager.getCache("pageNodes")).thenReturn(pageNodes);
        when(cacheManager.getCache("graphDetail")).thenReturn(graphDetail);
    }

    @Test
    @DisplayName("Node 조회 캐시 두 종류를 모두 무효화")
    void invalidates_both_node_query_caches() {
        invalidator.invalidate(10L);

        verify(pageNodes).evict(10L);
        verify(graphDetail).evict(10L);
    }

    @Test
    @DisplayName("첫 캐시 삭제가 실패해도 두 번째 캐시 삭제를 시도하고 실패 캐시를 보존")
    void attempts_every_cache_and_reports_failures() {
        doThrow(new IllegalStateException("Redis 장애")).when(pageNodes).evict(10L);

        assertThatThrownBy(() -> invalidator.invalidate(10L))
                .isInstanceOfSatisfying(
                        CacheInvalidationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getFailedCaches())
                                .containsExactly("pageNodes")
                );

        verify(graphDetail).evict(10L);
    }
}
