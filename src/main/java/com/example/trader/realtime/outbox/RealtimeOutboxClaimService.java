package com.example.trader.realtime.outbox;

import com.example.trader.repository.RealtimeOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RealtimeOutboxClaimService {

    private final RealtimeOutboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RealtimeOutbox> claimByEventId(String eventId) {
        if (repository.claimPending(eventId, LocalDateTime.now()) != 1) {
            return Optional.empty();
        }
        return repository.findById(eventId);
    }

    @Transactional(readOnly = true)
    public List<String> findPendingEventIds() {
        return repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
                .stream()
                .map(RealtimeOutbox::getEventId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(String eventId) {
        repository.findById(eventId)
                .filter(outbox -> outbox.getStatus() == OutboxStatus.PUBLISHING)
                .ifPresent(RealtimeOutbox::markSent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPendingForRetry(String eventId, String reason) {
        repository.findById(eventId)
                .filter(outbox -> outbox.getStatus() == OutboxStatus.PUBLISHING)
                .ifPresent(outbox -> {
                    outbox.markFailed(reason);
                    outbox.markPendingForRetry();
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStalePublishing(LocalDateTime cutoff) {
        return repository.recoverStalePublishing(cutoff);
    }
}
