package com.example.trader.repository;

import com.example.trader.realtime.outbox.OutboxStatus;
import com.example.trader.realtime.outbox.RealtimeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutbox, String> {

    List<RealtimeOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query("""
            select o.eventId from RealtimeOutbox o
             where o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING
               and (o.nextAttemptAt is null or o.nextAttemptAt <= :now)
               and (o.retryDeadlineAt is null or o.retryDeadlineAt > :now)
             order by o.createdAt asc
            """)
    List<String> findDueEventIds(@Param("now") LocalDateTime now, Pageable pageable);

    default int claimPending(String eventId, String claimToken, String claimedBy,
                             LocalDateTime claimedAt, LocalDateTime leaseUntil) {
        return claimPendingBeforeDeadline(eventId, claimToken, claimedBy, claimedAt, leaseUntil,
                claimedAt.plusMinutes(3));
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING,
                   o.retryCount = o.retryCount + 1,
                   o.totalAttemptCount = o.totalAttemptCount + 1,
                   o.firstAttemptAt = coalesce(o.firstAttemptAt, :claimedAt),
                   o.retryDeadlineAt = coalesce(o.retryDeadlineAt, :deadline),
                   o.kafkaRecoveryPending = false,
                   o.lastTriedAt = :claimedAt,
                   o.claimToken = :claimToken,
                   o.claimedBy = :claimedBy,
                   o.leaseUntil = :leaseUntil
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING
               and (o.nextAttemptAt is null or o.nextAttemptAt <= :claimedAt)
               and (o.retryDeadlineAt is null or o.retryDeadlineAt > :claimedAt)
            """)
    int claimPendingBeforeDeadline(@Param("eventId") String eventId,
                     @Param("claimToken") String claimToken,
                     @Param("claimedBy") String claimedBy,
                     @Param("claimedAt") LocalDateTime claimedAt,
                     @Param("leaseUntil") LocalDateTime leaseUntil,
                     @Param("deadline") LocalDateTime deadline);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.SENT,
                   o.sentAt = :completedAt,
                   o.lastTriedAt = :completedAt,
                   o.lastErrorMessage = null,
                   o.lastFailureCode = null,
                   o.nextAttemptAt = null,
                   o.kafkaRecoveryPending = false,
                   o.claimToken = null,
                   o.claimedBy = null,
                   o.leaseUntil = null
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
               and o.claimToken = :claimToken
            """)
    int markSentIfOwned(@Param("eventId") String eventId,
                        @Param("claimToken") String claimToken,
                        @Param("completedAt") LocalDateTime completedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING,
                   o.lastTriedAt = :failedAt,
                   o.lastErrorMessage = :reason,
                   o.lastFailureCode = :failureCode,
                   o.nextAttemptAt = :nextAttemptAt,
                   o.kafkaRecoveryPending = :recoveryPending,
                   o.claimToken = null,
                   o.claimedBy = null,
                   o.leaseUntil = null
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
               and o.claimToken = :claimToken
            """)
    int scheduleRetryIfOwned(@Param("eventId") String eventId,
                                   @Param("claimToken") String claimToken,
                                   @Param("reason") String reason,
                                   @Param("failedAt") LocalDateTime failedAt,
                                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                                   @Param("failureCode") String failureCode,
                                   @Param("recoveryPending") boolean recoveryPending);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.FAILED,
                   o.lastTriedAt = :failedAt,
                   o.lastErrorMessage = :reason,
                   o.lastFailureCode = :failureCode,
                   o.nextAttemptAt = null,
                   o.kafkaRecoveryPending = false,
                   o.claimToken = null,
                   o.claimedBy = null,
                   o.leaseUntil = null
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
               and o.claimToken = :claimToken
            """)
    int markFailedIfOwned(@Param("eventId") String eventId,
                          @Param("claimToken") String claimToken,
                          @Param("reason") String reason,
                          @Param("failedAt") LocalDateTime failedAt,
                          @Param("failureCode") String failureCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING,
                   o.lastErrorMessage = coalesce(o.lastErrorMessage, 'stale publishing recovered'),
                   o.claimToken = null,
                   o.claimedBy = null,
                   o.leaseUntil = null
             where o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
               and (o.leaseUntil is null or o.leaseUntil < :now)
            """)
    int recoverExpiredPublishing(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.FAILED,
                   o.lastFailureCode = 'RETRY_DEADLINE_EXCEEDED',
                   o.nextAttemptAt = null,
                   o.kafkaRecoveryPending = false,
                   o.claimToken = null, o.claimedBy = null, o.leaseUntil = null
             where o.retryDeadlineAt <= :now
               and (o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING
                    or (o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
                        and (o.leaseUntil is null or o.leaseUntil <= :now)))
            """)
    int expireRetryDeadlines(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.retryCount = 0, o.kafkaRecoveryPending = false
             where o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING
               and o.kafkaRecoveryPending = true
               and o.lastFailureCode = 'PUBLISH_UNAVAILABLE'
               and o.lastTriedAt <= :recoveryAt
               and o.retryDeadlineAt > :now
            """)
    int resetKafkaAttemptsAfterRecovery(@Param("recoveryAt") LocalDateTime recoveryAt,
                                       @Param("now") LocalDateTime now);
}
