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
                   o.lastErrorMessage = null
             where o.eventId = :eventId
               and o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING
            """)
    int claimPending(@Param("eventId") String eventId, @Param("claimedAt") LocalDateTime claimedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RealtimeOutbox o
               set o.status = com.example.trader.realtime.outbox.OutboxStatus.PENDING,
                   o.lastErrorMessage = 'stale publishing recovered'
             where o.status = com.example.trader.realtime.outbox.OutboxStatus.PUBLISHING
               and o.lastTriedAt < :cutoff
            """)
    int recoverStalePublishing(@Param("cutoff") LocalDateTime cutoff);
}
