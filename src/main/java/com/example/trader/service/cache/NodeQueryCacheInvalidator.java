package com.example.trader.service.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NodeQueryCacheInvalidator {

    private static final List<String> NODE_QUERY_CACHES = List.of("pageNodes", "graphDetail");

    private final CacheManager cacheManager;

    /**
     * 두 캐시는 서로 독립적으로 삭제를 시도하고, 모든 시도가 끝난 뒤 실패를 반환한다.
     */
    public void invalidate(Long graphId) {
        List<String> failedCaches = new ArrayList<>();
        Throwable firstFailure = null;

        for (String cacheName : NODE_QUERY_CACHES) {
            try {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache == null) {
                    throw new IllegalStateException("캐시를 찾을 수 없습니다: " + cacheName);
                }
                cache.evict(graphId);
            } catch (Exception exception) {
                failedCaches.add(cacheName);
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }

        if (!failedCaches.isEmpty()) {
            throw new CacheInvalidationException(failedCaches, firstFailure);
        }
    }
}
