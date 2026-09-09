package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionalReliableEventService {

    private final RealtimeOutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.instance-id:local}")
    private String instanceId;

    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(RealtimeEnvelope envelope) {
        if (envelope.getType() != com.example.trader.realtime.message.RealtimeType.RELIABLE) {
            throw new IllegalArgumentException("Only RELIABLE events can be staged in the outbox");
        }
        RealtimeEnvelope event = envelope.ensureEventId().ensureOriginInstanceId(instanceId);
        outboxService.savePending(event);
        eventPublisher.publishEvent(new ReliableOutboxCommittedEvent(event.getEventId(), event));
    }
}
