package com.example.trader.repository;

import com.example.trader.entity.CacheInvalidationStatus;
import com.example.trader.entity.CacheInvalidationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CacheInvalidationTaskRepository extends JpaRepository<CacheInvalidationTask, Long> {

    List<CacheInvalidationTask> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            CacheInvalidationStatus status,
            LocalDateTime now
    );

    @Modifying
    @Query(value = """
            INSERT INTO cache_invalidation_task (
                graph_id, generation, status, retry_count, next_retry_at,
                last_error, created_at, updated_at
            ) VALUES (
                :graphId, :generation, 'PENDING', 0, :nextRetryAt,
                :lastError, :now, :now
            )
            ON CONFLICT (graph_id) DO UPDATE SET
                generation = EXCLUDED.generation,
                status = 'PENDING',
                retry_count = 0,
                next_retry_at = EXCLUDED.next_retry_at,
                last_error = EXCLUDED.last_error,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsertPending(
            @Param("graphId") Long graphId,
            @Param("generation") String generation,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
            delete from CacheInvalidationTask task
             where task.graphId = :graphId
               and task.generation = :generation
            """)
    int deleteIfOwned(
            @Param("graphId") Long graphId,
            @Param("generation") String generation
    );

    @Modifying
    @Query("""
            update CacheInvalidationTask task
               set task.status = :status,
                   task.retryCount = :retryCount,
                   task.nextRetryAt = :nextRetryAt,
                   task.lastError = :lastError,
                   task.updatedAt = :updatedAt
             where task.graphId = :graphId
               and task.generation = :generation
            """)
    int updateFailureIfOwned(
            @Param("graphId") Long graphId,
            @Param("generation") String generation,
            @Param("status") CacheInvalidationStatus status,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
