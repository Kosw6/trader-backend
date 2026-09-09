package com.example.trader.realtime.propagation;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SingleCommittedEventPropagatorTest {

    @Test
    void propagatesToLocalWebSocketWithoutExternalPublisher() {
        RawPresenceBroadcaster broadcaster = mock(RawPresenceBroadcaster.class);
        SingleCommittedEventPropagator propagator = new SingleCommittedEventPropagator(broadcaster);
        RealtimeEnvelope event = reliableEvent();

        propagator.propagate(event);

        verify(broadcaster).enqueueOrdered("team:1:graph:2", event);
    }

    private RealtimeEnvelope reliableEvent() {
        return RealtimeEnvelope.builder()
                .eventId("event-1")
                .type(RealtimeType.RELIABLE)
                .teamId(1L)
                .graphId(2L)
                .build();
    }
}
