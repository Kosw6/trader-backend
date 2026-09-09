package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.realtime.propagation.CommittedEventPropagator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeAfterCommitListenerTest {

    @Test
    void delegatesCommittedEventWithoutKnowingConcreteTransport() {
        CommittedEventPropagator propagator = mock(CommittedEventPropagator.class);
        RealtimeAfterCommitListener listener = new RealtimeAfterCommitListener(propagator);
        RealtimeEnvelope envelope = RealtimeEnvelope.builder()
                .eventId("event-1")
                .type(RealtimeType.RELIABLE)
                .teamId(1L)
                .graphId(2L)
                .build();

        listener.propagateCommittedEvent(new ReliableOutboxCommittedEvent("event-1", envelope));

        verify(propagator).propagate(envelope);
    }
}
