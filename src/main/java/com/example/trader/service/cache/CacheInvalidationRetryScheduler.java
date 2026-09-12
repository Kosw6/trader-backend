package com.example.trader.service.cache;

import com.example.trader.entity.CacheInvalidationTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationRetryScheduler {

    private final CacheInvalidationRetryService retryService;
    private final NodeQueryCacheInvalidator cacheInvalidator;

    @Scheduled(fixedDelayString = "${cache.invalidation.retry-delay-ms:1000}")
    public void retryPending() {
        for (CacheInvalidationTask task : retryService.findDue(LocalDateTime.now())) {
            try {
                retry(task);
            } catch (Exception unexpectedFailure) {
                // 한 작업의 DB 상태 기록 실패가 뒤의 graph 재시도를 막지 않게 한다.
                log.error("캐시 무효화 재시도 처리 실패 graphId={} reason={}",
                        task.getGraphId(), unexpectedFailure.getMessage(), unexpectedFailure);
            }
        }
    }

    private void retry(CacheInvalidationTask task) {
        try {
            cacheInvalidator.invalidate(task.getGraphId());
            boolean completed = retryService.completeIfOwned(task);
            if (completed) {
                log.info("캐시 무효화 재시도 성공 graphId={} retryCount={}",
                        task.getGraphId(), task.getRetryCount());
            }
        } catch (Exception failure) {
            boolean updated = retryService.recordRetryFailure(task, failure, LocalDateTime.now());
            if (updated) {
                log.warn("캐시 무효화 재시도 실패 graphId={} retryCount={} reason={}",
                        task.getGraphId(), task.getRetryCount() + 1, failure.getMessage());
            }
        }
    }
}
