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

    @Test
    void new_process_cache_allows_old_event_to_be_propagated_again() {
        RawPresenceBroadcaster broadcaster = mock(RawPresenceBroadcaster.class);
        RealtimeEnvelope event = event();
        assertThat(handler(broadcaster).handle(event)).isTrue();
        // 재시작 후 새 캐시: 영구적인 exactly-once가 아님을 계약으로 남긴다.
        assertThat(handler(broadcaster).handle(event)).isTrue();
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
