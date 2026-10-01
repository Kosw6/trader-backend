package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.*;
import com.example.trader.repository.RealtimeOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class OutboxRetryWindowTest {
    static final LocalDateTime START = LocalDateTime.of(2026, 9, 19, 10, 0);
    @Autowired RealtimeOutboxRepository repository;

    void pending(String id) {
        repository.saveAndFlush(RealtimeOutbox.pending(RealtimeEnvelope.builder()
                .eventId(id).type(RealtimeType.RELIABLE).subType(RealtimeSubType.NODE_UPDATED)
                .teamId(1L).graphId(2L).payload(Map.of("version", 4)).build(), new ObjectMapper()));
    }

    RealtimeOutboxClaimService serviceAt(LocalDateTime now) {
        return new RealtimeOutboxClaimService(repository, "server", 30_000,
                Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    void claim(String id, String token, LocalDateTime at) {
        assertThat(repository.claimPending(id, token, "server", at, at.plusSeconds(30))).isEqualTo(1);
    }

    @Test
    void first_attempt_sets_fixed_window_and_no_attempt_means_no_deadline() {
        pending("event");
        serviceAt(START.plusHours(1)).recoverExpiredPublishing(START.plusHours(1));
        assertThat(repository.findById("event").orElseThrow().getFirstAttemptAt()).isNull();
        claim("event", "a", START);
        repository.scheduleRetryIfOwned("event", "a", "일시 실패", START.plusSeconds(1),
                START.plusSeconds(10), "PUBLISH_RETRYABLE", false);
        claim("event", "b", START.plusSeconds(10));
        var event = repository.findById("event").orElseThrow();
        assertThat(event.getFirstAttemptAt()).isEqualTo(START);
        assertThat(event.getRetryDeadlineAt()).isEqualTo(START.plusMinutes(3));
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getTotalAttemptCount()).isEqualTo(2);
    }

    @Test
    void due_time_is_enforced_by_selection_and_direct_claim() {
        pending("event");
        claim("event", "a", START);
        repository.scheduleRetryIfOwned("event", "a", "일시 실패", START.plusSeconds(1),
                START.plusSeconds(10), "PUBLISH_RETRYABLE", false);
        assertThat(repository.findDueEventIds(START.plusSeconds(9), PageRequest.of(0, 100))).isEmpty();
        assertThat(repository.claimPending("event", "b", "server", START.plusSeconds(9), START.plusMinutes(1))).isZero();
        assertThat(repository.findDueEventIds(START.plusSeconds(10), PageRequest.of(0, 100))).containsExactly("event");
        claim("event", "b", START.plusSeconds(10));
    }

    @Test
    void exact_deadline_refuses_claim_and_maintenance_marks_failed() {
        pending("event");
        claim("event", "a", START);
        repository.scheduleRetryIfOwned("event", "a", "원래 장애", START.plusSeconds(1),
                START.plusSeconds(10), "PUBLISH_UNAVAILABLE", true);
        assertThat(repository.claimPending("event", "b", "server", START.plusMinutes(3), START.plusMinutes(4))).isZero();
        assertThat(repository.findDueEventIds(START.plusMinutes(3), PageRequest.of(0, 100))).isEmpty();
        serviceAt(START.plusMinutes(3)).recoverExpiredPublishing(START.plusMinutes(3));
        var failed = repository.findById("event").orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getLastFailureCode()).isEqualTo("RETRY_DEADLINE_EXCEEDED");
        assertThat(failed.getLastErrorMessage()).isEqualTo("원래 장애");
        assertThat(serviceAt(START.plusMinutes(4)).resetKafkaAttemptsAfterRecovery(START.plusMinutes(4))).isZero();
    }

    @Test
    void active_claim_can_record_ack_after_deadline_but_expired_lease_cannot_restart() {
        pending("active");
        claim("active", "a", START);
        repository.scheduleRetryIfOwned("active", "a", "대기", START.plusSeconds(1),
                START.plusSeconds(170), "PUBLISH_RETRYABLE", false);
        claim("active", "b", START.plusSeconds(170));
        serviceAt(START.plusSeconds(180)).recoverExpiredPublishing(START.plusSeconds(180));
        assertThat(repository.findById("active").orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(repository.markSentIfOwned("active", "b", START.plusSeconds(181))).isEqualTo(1);

        pending("abandoned");
        claim("abandoned", "a", START);
        serviceAt(START.plusMinutes(3)).recoverExpiredPublishing(START.plusMinutes(3));
        assertThat(repository.findById("abandoned").orElseThrow().getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(repository.claimPending("abandoned", "b", "server", START.plusMinutes(4), START.plusMinutes(5))).isZero();
    }

    @Test
    void failure_after_deadline_is_terminal_even_with_active_lease() {
        pending("event");
        claim("event", "a", START);
        assertThat(serviceAt(START.plusMinutes(3)).markPendingForRetry("event", "a", "SENT_RECORD_FAILED: DB 장애"))
                .isTrue();
        var event = repository.findById("event").orElseThrow();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getLastFailureCode()).isEqualTo("RETRY_DEADLINE_EXCEEDED");
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void recovery_resets_once_per_failure_preserving_total_deadline_payload_and_schedule() {
        pending("event");
        claim("event", "a", START);
        var original = repository.findById("event").orElseThrow().getEnvelopeJson();
        repository.scheduleRetryIfOwned("event", "a", "PUBLISH_UNAVAILABLE: 장애", START.plusSeconds(1),
                START.plusSeconds(10), "PUBLISH_UNAVAILABLE", true);
        assertThat(repository.resetKafkaAttemptsAfterRecovery(START.plusSeconds(2), START.plusSeconds(2))).isEqualTo(1);
        assertThat(repository.resetKafkaAttemptsAfterRecovery(START.plusSeconds(3), START.plusSeconds(3))).isZero();
        var reset = repository.findById("event").orElseThrow();
        assertThat(reset.getRetryCount()).isZero();
        assertThat(reset.getTotalAttemptCount()).isEqualTo(1);
        assertThat(reset.getRetryDeadlineAt()).isEqualTo(START.plusMinutes(3));
        assertThat(reset.getNextAttemptAt()).isEqualTo(START.plusSeconds(10));
        assertThat(reset.getEnvelopeJson()).isEqualTo(original);

        claim("event", "b", START.plusSeconds(10));
        repository.scheduleRetryIfOwned("event", "b", "PUBLISH_UNAVAILABLE: 재장애", START.plusSeconds(11),
                START.plusSeconds(20), "PUBLISH_UNAVAILABLE", true);
        assertThat(repository.resetKafkaAttemptsAfterRecovery(START.plusSeconds(3), START.plusSeconds(12))).isZero();
        assertThat(repository.resetKafkaAttemptsAfterRecovery(START.plusSeconds(12), START.plusSeconds(12))).isEqualTo(1);
        assertThat(repository.findById("event").orElseThrow().getTotalAttemptCount()).isEqualTo(2);
        assertThat(repository.findById("event").orElseThrow().getRetryDeadlineAt()).isEqualTo(START.plusMinutes(3));
    }

    @Test
    void recovery_excludes_db_failure_permanent_failure_and_active_claim() {
        for (String id : new String[]{"db", "permanent", "active"}) {
            pending(id);
            claim(id, "a", START);
        }
        serviceAt(START.plusSeconds(1)).markPendingForRetry("db", "a", "SENT_RECORD_FAILED: DB 오류");
        serviceAt(START.plusSeconds(1)).markFailed("permanent", "a", "INVALID_EVENT: 손상");
        assertThat(repository.resetKafkaAttemptsAfterRecovery(START.plusSeconds(2), START.plusSeconds(2))).isZero();
        assertThat(repository.findById("active").orElseThrow().getClaimToken()).isEqualTo("a");
    }

    @Test
    void service_schedules_backoff_and_stale_token_cannot_change_retry() {
        pending("event");
        claim("event", "a", START);
        assertThat(serviceAt(START.plusSeconds(1)).markPendingForRetry("event", "stale", "PUBLISH_UNKNOWN: 오류"))
                .isFalse();
        assertThat(serviceAt(START.plusSeconds(1)).markPendingForRetry("event", "a", "PUBLISH_UNKNOWN: 오류"))
                .isTrue();
        assertThat(repository.findById("event").orElseThrow().getNextAttemptAt())
                .isBetween(START.plusSeconds(6), START.plusSeconds(7));
    }
}
