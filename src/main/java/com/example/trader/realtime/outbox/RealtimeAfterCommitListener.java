package com.example.trader.realtime.outbox;

import com.example.trader.realtime.propagation.CommittedEventPropagator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RealtimeAfterCommitListener {

    private final CommittedEventPropagator committedEventPropagator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void propagateCommittedEvent(ReliableOutboxCommittedEvent event) {
        committedEventPropagator.propagate(event.envelope());
    }
}
