package com.example.trader.realtime.outbox;

import com.example.trader.repository.RealtimeOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RealtimeOutboxClaimService {

    private final RealtimeOutboxRepository repository;
    private final String instanceId;
    private final long claimLeaseMs;
    private final Clock clock;

    @Autowired
    public RealtimeOutboxClaimService(
            RealtimeOutboxRepository repository,
            @Value("${app.instance-id:local}") String instanceId,
            @Value("${realtime.outbox.claim-lease-ms:${realtime.outbox.stale-after-ms:30000}}") long claimLeaseMs
    ) {
        this(repository, instanceId, claimLeaseMs, Clock.systemDefaultZone());
    }

    RealtimeOutboxClaimService(RealtimeOutboxRepository repository, String instanceId,
                               long claimLeaseMs, Clock clock) {
        this.repository = repository;
        this.instanceId = instanceId;
        if (claimLeaseMs <= 0) {
            throw new IllegalArgumentException("realtime.outbox.claim-lease-ms must be greater than zero");
        }
        this.claimLeaseMs = claimLeaseMs;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedRealtimeOutbox> claimByEventId(String eventId) {
        LocalDateTime claimedAt = LocalDateTime.now(clock);
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
        return repository.findDueEventIds(LocalDateTime.now(clock), PageRequest.of(0, 100));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(String eventId, String claimToken) {
        return repository.markSentIfOwned(eventId, claimToken, LocalDateTime.now(clock)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPendingForRetry(String eventId, String claimToken, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<RealtimeOutbox> owned = repository.findById(eventId)
                .filter(o -> o.getStatus() == OutboxStatus.PUBLISHING && claimToken.equals(o.getClaimToken()));
        if (owned.isEmpty()) {
            return false;
        }
        RealtimeOutbox outbox = owned.get();
        String truncatedReason = reason == null
                ? null
                : reason.substring(0, Math.min(reason.length(), 1000));
        if (outbox.getRetryDeadlineAt() != null && !now.isBefore(outbox.getRetryDeadlineAt())) {
            return repository.markFailedIfOwned(eventId, claimToken, truncatedReason, now,
                    "RETRY_DEADLINE_EXCEEDED") == 1;
        }
        String code = failureCode(reason);
        return repository.scheduleRetryIfOwned(eventId, claimToken, truncatedReason, now,
                OutboxRetryPolicy.nextAttempt(now, outbox.getRetryCount(), outbox.getRetryDeadlineAt()),
                code, "PUBLISH_UNAVAILABLE".equals(code)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(String eventId, String claimToken, String reason) {
        String truncatedReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000));
        return repository.markFailedIfOwned(eventId, claimToken, truncatedReason,
                LocalDateTime.now(clock), failureCode(reason)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpiredPublishing(LocalDateTime now) {
        // 기한 초과 작업은 PENDING으로 되돌리기 전에 종료한다. 활성 lease는 건드리지 않는다.
        repository.expireRetryDeadlines(now);
        return repository.recoverExpiredPublishing(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int resetKafkaAttemptsAfterRecovery(LocalDateTime recoveryAt) {
        return repository.resetKafkaAttemptsAfterRecovery(recoveryAt, LocalDateTime.now(clock));
    }

    private String failureCode(String reason) {
        if (reason == null) return "UNKNOWN";
        String prefix = reason.split(":", 2)[0];
        try {
            return OutboxFailure.valueOf(prefix).name();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN";
        }
    }
}
