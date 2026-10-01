package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class RealtimeOutboxRelayTest {

    @Test
    void marksSentOnlyAfterKafkaAcknowledges() throws Exception {
        RealtimeOutboxClaimService claimService = mock(RealtimeOutboxClaimService.class);
        KafkaReliablePublisher kafkaPublisher = mock(KafkaReliablePublisher.class);
        KafkaHealthState healthState = mock(KafkaHealthState.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeOutboxRelay relay = new RealtimeOutboxRelay(
                claimService, kafkaPublisher, healthState, objectMapper
        );
        RealtimeEnvelope envelope = RealtimeEnvelope.builder()
                .eventId("event-1")
                .payload(java.util.Map.of("version", 4))
                .type(RealtimeType.RELIABLE)
                .subType(RealtimeSubType.NODE_UPDATED)
                .teamId(1L)
                .graphId(2L)
                .build();
        RealtimeOutbox pending = RealtimeOutbox.pending(envelope, objectMapper);
        ClaimedRealtimeOutbox outbox = new ClaimedRealtimeOutbox(
                "event-1", "claim-1", 2L, RealtimeSubType.NODE_UPDATED, pending.getEnvelopeJson()
        );

        when(healthState.isAvailable()).thenReturn(true);
        when(claimService.claimByEventId("event-1")).thenReturn(Optional.of(outbox));
        when(claimService.markSent("event-1", "claim-1")).thenReturn(true);

        relay.publishByEventId("event-1");

        var ordered = inOrder(kafkaPublisher, claimService);
        ordered.verify(kafkaPublisher).publishOrThrow(any(RealtimeEnvelope.class));
        ordered.verify(claimService).markSent("event-1", "claim-1");
        verify(claimService, never()).markPendingForRetry(anyString(), anyString(), anyString());
    }

    @Test
    void leavesEventForRetryWhenKafkaFails() throws Exception {
        RealtimeOutboxClaimService claimService = mock(RealtimeOutboxClaimService.class);
        KafkaReliablePublisher kafkaPublisher = mock(KafkaReliablePublisher.class);
        KafkaHealthState healthState = mock(KafkaHealthState.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeOutboxRelay relay = new RealtimeOutboxRelay(
                claimService, kafkaPublisher, healthState, objectMapper
        );
        RealtimeEnvelope envelope = RealtimeEnvelope.builder()
                .eventId("event-2")
                .payload(java.util.Map.of("version", 4))
                .type(RealtimeType.RELIABLE)
                .subType(RealtimeSubType.NODE_UPDATED)
                .teamId(1L)
                .graphId(2L)
                .build();
        RealtimeOutbox pending = RealtimeOutbox.pending(envelope, objectMapper);
        ClaimedRealtimeOutbox outbox = new ClaimedRealtimeOutbox(
                "event-2", "claim-2", 2L, RealtimeSubType.NODE_UPDATED, pending.getEnvelopeJson()
        );

        when(healthState.isAvailable()).thenReturn(true);
        when(claimService.claimByEventId("event-2")).thenReturn(Optional.of(outbox));
        when(claimService.markPendingForRetry("event-2", "claim-2", "PUBLISH_UNKNOWN: kafka unavailable"))
                .thenReturn(true);
        doThrow(new IllegalStateException("kafka unavailable"))
                .when(kafkaPublisher).publishOrThrow(any(RealtimeEnvelope.class));

        relay.publishByEventId("event-2");

        verify(claimService).markPendingForRetry("event-2", "claim-2", "PUBLISH_UNKNOWN: kafka unavailable");
        verify(claimService, never()).markSent(anyString(), anyString());
    }
}
