package com.example.trader.repository;

import com.example.trader.realtime.outbox.OutboxStatus;
import com.example.trader.realtime.outbox.RealtimeOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutbox, String> {

    List<RealtimeOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
