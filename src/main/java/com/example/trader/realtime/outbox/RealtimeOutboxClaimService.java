package com.example.trader.realtime.outbox;

import com.example.trader.repository.RealtimeOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RealtimeOutboxClaimService {

    private final RealtimeOutboxRepository repository;
    private final String instanceId;
    private final long claimLeaseMs;

    public RealtimeOutboxClaimService(
            RealtimeOutboxRepository repository,
            @Value("${app.instance-id:local}") String instanceId,
            @Value("${realtime.outbox.claim-lease-ms:${realtime.outbox.stale-after-ms:30000}}") long claimLeaseMs
    ) {
        this.repository = repository;
        this.instanceId = instanceId;
        if (claimLeaseMs <= 0) {
            throw new IllegalArgumentException("realtime.outbox.claim-lease-ms must be greater than zero");
        }
        this.claimLeaseMs = claimLeaseMs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedRealtimeOutbox> claimByEventId(String eventId) {
        LocalDateTime claimedAt = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = claimedAt.plus(Duration.ofMillis(claimLeaseMs));

        if (repository.claimPending(
                eventId,
                claimToken,
                instanceId,
                claimedAt,
                leaseUntil
        ) != 1) {
            return Optional.empty();
        }

        return repository.findById(eventId)
                .filter(outbox -> claimToken.equals(outbox.getClaimToken()))
                .map(ClaimedRealtimeOutbox::from);
    }

    @Transactional(readOnly = true)
    public List<String> findPendingEventIds() {
        return repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
                .stream()
                .map(RealtimeOutbox::getEventId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(String eventId, String claimToken) {
        return repository.markSentIfOwned(eventId, claimToken, LocalDateTime.now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPendingForRetry(String eventId, String claimToken, String reason) {
        String truncatedReason = reason == null
                ? null
                : reason.substring(0, Math.min(reason.length(), 1000));
        return repository.markPendingForRetryIfOwned(
                eventId,
                claimToken,
                truncatedReason,
                LocalDateTime.now()
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpiredPublishing(LocalDateTime now) {
        return repository.recoverExpiredPublishing(now);
    }
}
