package com.example.trader.realtime.inbound;

import com.example.trader.realtime.LocalReliableEventDeduplicator;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReliableInboundHandlerTest {

    @Test
    void propagatesSameEventOnlyOnceInCurrentProcess() {
        RawPresenceBroadcaster broadcaster = mock(RawPresenceBroadcaster.class);
        ReliableInboundHandler handler = handler(broadcaster);
        RealtimeEnvelope event = event();

        assertThat(handler.handle(event)).isTrue();
        assertThat(handler.handle(event)).isFalse();

        verify(broadcaster, times(1)).enqueueReliable("team:1:graph:2", event);
    }

    @Test
    void releasesClaimWhenLocalQueueingFails() {
        RawPresenceBroadcaster broadcaster = mock(RawPresenceBroadcaster.class);
        ReliableInboundHandler handler = handler(broadcaster);
        RealtimeEnvelope event = event();
        doThrow(new IllegalStateException("queue full"))
                .doNothing()
                .when(broadcaster).enqueueReliable("team:1:graph:2", event);

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("queue full");

        assertThat(handler.handle(event)).isTrue();
        verify(broadcaster, times(2)).enqueueReliable("team:1:graph:2", event);
    }

    private ReliableInboundHandler handler(RawPresenceBroadcaster broadcaster) {
        LocalReliableEventDeduplicator deduplicator =
                new LocalReliableEventDeduplicator(100, Duration.ofMinutes(10));
        return new ReliableInboundHandler(broadcaster, deduplicator);
    }

    private RealtimeEnvelope event() {
        return RealtimeEnvelope.builder()
                .eventId("event-1")
                .type(RealtimeType.RELIABLE)
                .teamId(1L)
                .graphId(2L)
                .build();
    }
}
