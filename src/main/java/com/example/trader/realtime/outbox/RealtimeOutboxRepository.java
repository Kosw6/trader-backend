package com.example.trader.realtime.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RealtimeOutboxRepository extends JpaRepository<RealtimeOutbox, String> {

    List<RealtimeOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
