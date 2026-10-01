package com.example.trader.realtime.propagation;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SingleCommittedEventPropagatorTest {

    @Test
    void propagatesToLocalWebSocketWithoutExternalPublisher() {
        ReliableInboundHandler reliableInboundHandler = mock(ReliableInboundHandler.class);
        SingleCommittedEventPropagator propagator = new SingleCommittedEventPropagator(reliableInboundHandler);
        RealtimeEnvelope event = reliableEvent();

        propagator.propagate(event);

        verify(reliableInboundHandler).handle(event);
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
