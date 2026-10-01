package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.message.*;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeOutboxFailureTest {
    final RealtimeOutboxClaimService claims = mock(RealtimeOutboxClaimService.class);
    final KafkaReliablePublisher publisher = mock(KafkaReliablePublisher.class);
    final KafkaHealthState health = spy(new KafkaHealthState());
    final ObjectMapper mapper = new ObjectMapper();
    final RealtimeOutboxRelay relay = new RealtimeOutboxRelay(claims, publisher, health, mapper);

    ClaimedRealtimeOutbox claim(String id, String token) throws Exception {
        var envelope = RealtimeEnvelope.builder().eventId(id).type(RealtimeType.RELIABLE)
                .subType(RealtimeSubType.NODE_UPDATED).teamId(1L).graphId(2L)
                .payload(Map.of("version", 4, "fields", List.of("content"))).build();
        return new ClaimedRealtimeOutbox(id, token, 2L, envelope.getSubType(), mapper.writeValueAsString(envelope));
    }

    void available(String id) throws Exception {
        when(claims.claimByEventId(id)).thenReturn(Optional.of(claim(id, "token")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "null", "{}", "{\"eventId\":\"other\",\"type\":\"RELIABLE\"}"})
    void invalid_data_does_not_poison_health_or_stop_next_event(String json) throws Exception {
        when(claims.findPendingEventIds()).thenReturn(List.of("bad", "good"));
        when(claims.claimByEventId("bad")).thenReturn(Optional.of(new ClaimedRealtimeOutbox(
                "bad", "token", 2L, RealtimeSubType.NODE_UPDATED, json)));
        available("good");
        relay.publishPendingBatch();
        verify(claims).markFailed(eq("bad"), eq("token"), startsWith("INVALID_EVENT:"));
        verify(publisher, times(1)).publishOrThrow(argThat(e -> e.getEventId().equals("good")));
        verify(health, never()).markDown();
    }

    @Test
    void timeout_republishes_original_id_payload_with_new_claim() throws Exception {
        when(claims.claimByEventId("event")).thenReturn(
                Optional.of(claim("event", "first")), Optional.of(claim("event", "second")));
        doThrow(new ExecutionException(new TimeoutException("ACK 대기 초과")))
                .doNothing().when(publisher).publishOrThrow(any());
        relay.publishByEventId("event");
        assertThat(health.isAvailable()).isFalse();
        verify(claims).markPendingForRetry(eq("event"), eq("first"), startsWith("PUBLISH_UNAVAILABLE:"));
        health.markUp(); // 가용성 점검을 통한 회복
        relay.publishByEventId("event");
        assertSameEventRepublished();
        verify(claims).markSent("event", "second");
    }

    @Test
    void ack_then_db_failure_republishes_without_marking_kafka_down() throws Exception {
        when(claims.claimByEventId("event")).thenReturn(
                Optional.of(claim("event", "first")), Optional.of(claim("event", "second")));
        when(claims.markSent("event", "first")).thenThrow(new TransactionSystemException("커밋 실패"));
        relay.publishByEventId("event");
        verify(claims).markPendingForRetry(eq("event"), eq("first"), startsWith("SENT_RECORD_FAILED:"));
        relay.publishByEventId("event");
        assertSameEventRepublished();
        verify(health, never()).markDown();
    }

    void assertSameEventRepublished() throws Exception {
        var sent = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(publisher, times(2)).publishOrThrow(sent.capture());
        assertThat(sent.getAllValues()).extracting(RealtimeEnvelope::getEventId).containsExactly("event", "event");
        assertThat(sent.getAllValues().get(0).getPayload()).isEqualTo(sent.getAllValues().get(1).getPayload());
    }

    @Test
    void lost_ownership_does_not_trigger_retry() throws Exception {
        available("event");
        when(claims.markSent("event", "token")).thenReturn(false);
        relay.publishByEventId("event");
        verify(claims, never()).markPendingForRetry(any(), any(), any());
        verify(claims, never()).markFailed(any(), any(), any());
        verify(health, never()).markDown();
    }

    @Test
    void recovery_record_failure_does_not_stop_next_event() throws Exception {
        available("bad");
        available("good");
        when(claims.findPendingEventIds()).thenReturn(List.of("bad", "good"));
        when(claims.markSent("bad", "token")).thenThrow(new DataAccessResourceFailureException("DB 단절"));
        when(claims.markPendingForRetry(eq("bad"), eq("token"), anyString()))
                .thenThrow(new DataAccessResourceFailureException("복구 기록 실패"));
        relay.publishPendingBatch();
        verify(claims).markSent("good", "token");
        verify(health, never()).markDown();
    }

    @Test
    void permanent_publish_failure_is_quarantined_without_marking_down() throws Exception {
        available("event");
        doThrow(new ExecutionException(new org.apache.kafka.common.errors.SerializationException("잘못된 값")))
                .when(publisher).publishOrThrow(any());
        relay.publishByEventId("event");
        verify(claims).markFailed(eq("event"), eq("token"), startsWith("PUBLISH_PERMANENT:"));
        verify(health, never()).markDown();
        verify(claims, never()).markSent(any(), any());
    }

    @Test
    void interrupted_publish_preserves_flag_and_stops_batch() throws Exception {
        available("first");
        when(claims.findPendingEventIds()).thenReturn(List.of("first", "second"));
        doThrow(new ExecutionException(new InterruptedException("작업 중단")))
                .when(publisher).publishOrThrow(any());
        try {
            relay.publishPendingBatch();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(claims, never()).claimByEventId("second");
            verify(health, never()).markDown();
            verify(claims).markPendingForRetry(eq("first"), eq("token"), startsWith("INTERRUPTED:"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void claim_db_failure_does_not_stop_next_event() throws Exception {
        when(claims.findPendingEventIds()).thenReturn(List.of("bad", "good"));
        when(claims.claimByEventId("bad")).thenThrow(new DataAccessResourceFailureException("선점 실패"));
        available("good");
        relay.publishPendingBatch();
        verify(claims).markSent("good", "token");
        verify(health, never()).markDown();
    }
}
