package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxRecoverySchedulerTest {
    @Test
    void down_skips_claims_but_still_cleans_deadlines() {
        var claims = mock(RealtimeOutboxClaimService.class);
        var health = new KafkaHealthState();
        health.markDown();
        var relay = new RealtimeOutboxRelay(claims, mock(KafkaReliablePublisher.class), health, new ObjectMapper());
        relay.publishByEventId("event");
        verifyNoInteractions(claims);
        var scheduledRelay = mock(RealtimeOutboxRelay.class);
        new RealtimeOutboxRepublishScheduler(scheduledRelay, claims, health).republishPendingEvents();
        verify(claims).recoverExpiredPublishing(any());
        verifyNoInteractions(scheduledRelay);
        verify(claims, never()).resetKafkaAttemptsAfterRecovery(any());
    }

    @Test
    void repeated_up_and_send_ack_do_not_create_new_recovery_signals() {
        var health = new KafkaHealthState();
        var now = LocalDateTime.of(2026, 9, 19, 10, 0);
        health.markUp();
        assertThat(health.confirmedRecoveryAt()).isNull();
        health.confirmProbe(true, now);
        health.confirmProbe(true, now.plusSeconds(5));
        assertThat(health.confirmedRecoveryAt()).isEqualTo(now);
        health.confirmProbe(false, now.plusSeconds(10));
        assertThat(health.isAvailable()).isFalse();
        health.markUp(); // 발행 ACK가 health를 올려도 probe의 장애 구간은 유지한다.
        health.confirmProbe(true, now.plusSeconds(15));
        assertThat(health.confirmedRecoveryAt()).isEqualTo(now.plusSeconds(15));
    }

    @Test
    void recovery_is_retried_by_scheduler_before_dispatch_and_db_guards_idempotency() {
        var claims = mock(RealtimeOutboxClaimService.class);
        var relay = mock(RealtimeOutboxRelay.class);
        var health = new KafkaHealthState();
        var now = LocalDateTime.now();
        health.confirmProbe(true, now);
        var scheduler = new RealtimeOutboxRepublishScheduler(relay, claims, health);
        scheduler.republishPendingEvents();
        var order = inOrder(claims, relay);
        order.verify(claims).recoverExpiredPublishing(any());
        order.verify(claims).resetKafkaAttemptsAfterRecovery(now);
        order.verify(relay).publishPendingBatch();
    }
}
