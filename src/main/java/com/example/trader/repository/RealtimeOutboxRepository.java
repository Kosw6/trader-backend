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

@Repository
public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutbox, String> {

    List<RealtimeOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING,
                   o.retryCount = o.retryCount + 1,
                   o.lastTriedAt = :claimedAt,
                   o.lastErrorMessage = null,
                   o.claimToken = :claimToken,
                   o.claimedBy = :claimedBy,
                   o.leaseUntil = :leaseUntil
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING
            """)
    int claimPending(@Param("eventId") String eventId,
                     @Param("claimToken") String claimToken,
                     @Param("claimedBy") String claimedBy,
                     @Param("claimedAt") LocalDateTime claimedAt,
                     @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.SENT,
                   o.sentAt = :completedAt,
                   o.lastTriedAt = :completedAt,
                   o.lastErrorMessage = null,
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
                   o.claimToken = null,
                   o.claimedBy = null,
                   o.leaseUntil = null
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
               and o.claimToken = :claimToken
            """)
    int markPendingForRetryIfOwned(@Param("eventId") String eventId,
                                   @Param("claimToken") String claimToken,
                                   @Param("reason") String reason,
                                   @Param("failedAt") LocalDateTime failedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.FAILED,
                   o.lastTriedAt = :failedAt,
                   o.lastErrorMessage = :reason,
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
                          @Param("failedAt") LocalDateTime failedAt);

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
}
