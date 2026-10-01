package com.example.trader.service.cache;

import com.example.trader.entity.CacheInvalidationStatus;
import com.example.trader.entity.CacheInvalidationTask;
import com.example.trader.repository.CacheInvalidationTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CacheInvalidationRetryService {

    static final int MAX_RETRY_COUNT = 5;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5)
    );

    private final CacheInvalidationTaskRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInitialFailure(Long graphId, Throwable failure) {
        LocalDateTime now = LocalDateTime.now();
        repository.upsertPending(
                graphId,
                UUID.randomUUID().toString(),
                now.plus(RETRY_DELAYS.get(0)),
                errorMessage(failure),
                now
        );
    }

    @Transactional(readOnly = true)
    public List<CacheInvalidationTask> findDue(LocalDateTime now) {
        return repository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                CacheInvalidationStatus.PENDING,
                now
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeIfOwned(CacheInvalidationTask task) {
        return repository.deleteIfOwned(task.getGraphId(), task.getGeneration()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordRetryFailure(
            CacheInvalidationTask task,
            Throwable failure,
            LocalDateTime now
    ) {
        int retryCount = task.getRetryCount() + 1;
        boolean exhausted = retryCount >= MAX_RETRY_COUNT;
        CacheInvalidationStatus status = exhausted
                ? CacheInvalidationStatus.FAILED
                : CacheInvalidationStatus.PENDING;
        LocalDateTime nextRetryAt = exhausted
                ? null
                : now.plus(RETRY_DELAYS.get(retryCount));

        return repository.updateFailureIfOwned(
                task.getGraphId(),
                task.getGeneration(),
                status,
                retryCount,
                nextRetryAt,
                errorMessage(failure),
                now
        ) == 1;
    }

    private String errorMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
