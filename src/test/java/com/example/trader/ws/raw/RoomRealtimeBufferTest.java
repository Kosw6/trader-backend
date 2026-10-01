package com.example.trader.ws.raw;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomRealtimeBufferTest {

    private static final String ROOM = "team:1:graph:2";

    @Test
    void keepsReliableAndTransientInSeparateOrderedQueues() {
        RoomRealtimeBuffer buffer = new RoomRealtimeBuffer(3, 3, 3);
        RealtimeEnvelope reliable = event("r1", RealtimeType.RELIABLE);
        RealtimeEnvelope transientEvent = event("t1", RealtimeType.TRANSIENT);

        buffer.offerTransient(ROOM, transientEvent);
        buffer.offerReliable(ROOM, reliable);

        RoomRealtimeBuffer.Drain drain = buffer.drain(ROOM);
        assertThat(drain.reliableEvents()).containsExactly(reliable);
        assertThat(drain.transientEvents()).containsExactly(transientEvent);
        assertThat(drain.persistentResyncRequired()).isFalse();
        assertThat(drain.transientSnapshotRequired()).isFalse();
    }

    @Test
    void reliableOverflowUsesPriorityResyncSlotOutsideQueue() {
        RoomRealtimeBuffer buffer = new RoomRealtimeBuffer(1, 2, 2);
        buffer.offerReliable(ROOM, event("r1", RealtimeType.RELIABLE));
        buffer.offerReliable(ROOM, event("r2", RealtimeType.RELIABLE));

        RoomRealtimeBuffer.Drain drain = buffer.drain(ROOM);
        assertThat(drain.persistentResyncRequired()).isTrue();
        assertThat(drain.reliableEvents()).isEmpty();
        assertThat(drain.transientSnapshotRequired()).isFalse();
    }

    @Test
    void transientOverflowUsesPrioritySnapshotSlotWithoutAffectingReliable() {
        RoomRealtimeBuffer buffer = new RoomRealtimeBuffer(2, 1, 2);
        RealtimeEnvelope reliable = event("r1", RealtimeType.RELIABLE);
        buffer.offerReliable(ROOM, reliable);
        buffer.offerTransient(ROOM, event("t1", RealtimeType.TRANSIENT));
        buffer.offerTransient(ROOM, event("t2", RealtimeType.TRANSIENT));

        RoomRealtimeBuffer.Drain drain = buffer.drain(ROOM);
        assertThat(drain.transientSnapshotRequired()).isTrue();
        assertThat(drain.transientEvents()).isEmpty();
        assertThat(drain.reliableEvents()).containsExactly(reliable);
        assertThat(drain.persistentResyncRequired()).isFalse();
    }

    private RealtimeEnvelope event(String id, RealtimeType type) {
        return RealtimeEnvelope.builder().eventId(id).type(type).build();
    }
}
