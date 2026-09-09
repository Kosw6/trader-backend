package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.repository.RealtimeOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RealtimeOutboxRepositoryClaimTest {

    @Autowired RealtimeOutboxRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("현재 claim token 소유자만 완료하거나 재시도 상태로 변경할 수 있다")
    void stale_worker_cannot_change_new_claim_state() {
        repository.saveAndFlush(pending("event-1"));

        LocalDateTime firstClaimedAt = LocalDateTime.now();
        LocalDateTime firstLeaseUntil = firstClaimedAt.plusSeconds(30);
        assertThat(repository.claimPending(
                "event-1", "token-A", "server-A", firstClaimedAt, firstLeaseUntil
        )).isEqualTo(1);

        // PUBLISHING 상태인 동안 다른 작업자는 최초 선점에 실패한다.
        assertThat(repository.claimPending(
                "event-1", "token-B", "server-B", firstClaimedAt, firstLeaseUntil
        )).isZero();

        // A의 lease가 만료된 뒤 B가 새로운 token으로 재선점한다.
        // DB timestamp 정밀도 차이에 영향받지 않도록 lease보다 충분히 뒤인 시각을 사용한다.
        assertThat(repository.recoverExpiredPublishing(firstLeaseUntil.plusSeconds(1))).isEqualTo(1);
        LocalDateTime secondClaimedAt = firstLeaseUntil.plusSeconds(1);
        assertThat(repository.claimPending(
                "event-1", "token-B", "server-B", secondClaimedAt, secondClaimedAt.plusSeconds(30)
        )).isEqualTo(1);

        // 늦게 돌아온 A는 B의 상태를 SENT나 PENDING으로 바꿀 수 없다.
        assertThat(repository.markSentIfOwned(
                "event-1", "token-A", secondClaimedAt.plusSeconds(1)
        )).isZero();
        assertThat(repository.markPendingForRetryIfOwned(
                "event-1", "token-A", "late failure", secondClaimedAt.plusSeconds(1)
        )).isZero();

        RealtimeOutbox claimedByB = repository.findById("event-1").orElseThrow();
        assertThat(claimedByB.getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(claimedByB.getClaimToken()).isEqualTo("token-B");
        assertThat(claimedByB.getClaimedBy()).isEqualTo("server-B");

        assertThat(repository.markSentIfOwned(
                "event-1", "token-B", secondClaimedAt.plusSeconds(2)
        )).isEqualTo(1);
        RealtimeOutbox sent = repository.findById("event-1").orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sent.getClaimToken()).isNull();
        assertThat(sent.getLeaseUntil()).isNull();
    }

    private RealtimeOutbox pending(String eventId) {
        return RealtimeOutbox.pending(
                RealtimeEnvelope.builder()
                        .eventId(eventId)
                        .type(RealtimeType.RELIABLE)
                        .subType(RealtimeSubType.NODE_UPDATED)
                        .teamId(1L)
                        .graphId(2L)
                        .build(),
                objectMapper
        );
    }
}
