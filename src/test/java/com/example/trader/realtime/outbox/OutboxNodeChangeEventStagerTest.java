package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.service.event.NodeChange;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxNodeChangeEventStagerTest {
    private final TransactionalReliableEventService reliable = mock(TransactionalReliableEventService.class);
    private final OutboxNodeChangeEventStager stager = new OutboxNodeChangeEventStager(
            reliable, Clock.fixed(Instant.ofEpochMilli(12345), ZoneOffset.UTC));

    static Stream<Object[]> events() {
        return Stream.of(
                new Object[]{NodeChange.created(1L, 2L, 3L, 4L, 0), "NODE_CREATED", List.of("node"), 0, 0.0, 0.0},
                new Object[]{NodeChange.updated(1L, 2L, 3L, 4L, List.of("subject"), 5), "NODE_UPDATED", List.of("subject"), 5, 0.0, 0.0},
                new Object[]{NodeChange.deleted(1L, 2L, 3L), "NODE_DELETED", List.of("node"), null, 0.0, 0.0},
                new Object[]{NodeChange.positionUpdated(1L, 2L, 3L, 4L, 6, 12, 34), "NODE_POSITION_UPDATED", List.of("x", "y"), 6, 12.0, 34.0});
    }

    @ParameterizedTest
    @MethodSource("events")
    void preserves_existing_wire_contract(NodeChange change, String subtype, List<String> fields,
                                          Integer version, double x, double y) throws Exception {
        stager.stage(change);
        var captor = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(reliable).stage(captor.capture());
        var envelope = captor.getValue();
        assertThat(envelope.getType()).isEqualTo(RealtimeType.RELIABLE);
        assertThat(envelope.getSubType()).isEqualTo(RealtimeSubType.valueOf(subtype));
        assertThat(envelope.getTeamId()).isEqualTo(1L);
        assertThat(envelope.getGraphId()).isEqualTo(2L);
        var expected = new RawCursorMessage("__CONTROL__", subtype, 1L, 2L,
                change.kind() == NodeChange.Kind.DELETED ? null : 4L,
                null, 3L, x, y, 12345, fields, null, null, version);
        assertThat(envelope.getPayload()).isEqualTo(expected);
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(envelope.getPayload()));
        assertThat(json.has("nickName")).isFalse();
        assertThat(json.has("baseVersion")).isFalse();
        assertThat(json.has("lockTtlMs")).isFalse();
        if (change.kind() == NodeChange.Kind.DELETED) {
            assertThat(json.has("userId")).isFalse();
            assertThat(json.has("version")).isFalse();
        }
    }

    @Test
    void message_construction_failure_does_not_record_event() {
        var clock = mock(Clock.class);
        var failure = new IllegalStateException("메시지 시각 생성 실패");
        when(clock.millis()).thenThrow(failure);
        var failingStager = new OutboxNodeChangeEventStager(reliable, clock);
        assertThatThrownBy(() -> failingStager.stage(NodeChange.deleted(1L, 2L, 3L)))
                .isSameAs(failure);
        verifyNoInteractions(reliable);
    }

    @Test
    void propagates_recording_failure_without_retry_or_success_conversion() {
        var failure = new IllegalStateException("Outbox 기록 실패");
        doThrow(failure).when(reliable).stage(any());
        assertThatThrownBy(() -> stager.stage(NodeChange.deleted(1L, 2L, 3L))).isSameAs(failure);
        verify(reliable, times(1)).stage(any());
    }
}
