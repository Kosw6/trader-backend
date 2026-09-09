package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class TransactionalReliableEventServiceTest {

    @Test
    void stagesOutboxBeforeRegisteringAfterCommitEvent() {
        RealtimeOutboxService outboxService = mock(RealtimeOutboxService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        TransactionalReliableEventService service =
                new TransactionalReliableEventService(outboxService, eventPublisher);
        ReflectionTestUtils.setField(service, "instanceId", "ws-1");

        RealtimeEnvelope envelope = RealtimeEnvelope.builder()
                .type(RealtimeType.RELIABLE)
                .subType(RealtimeSubType.NODE_UPDATED)
                .teamId(1L)
                .graphId(2L)
                .build();

        service.stage(envelope);

        var ordered = inOrder(outboxService, eventPublisher);
        ordered.verify(outboxService).savePending(argThat(event -> event.getEventId() != null
                && "ws-1".equals(event.getOriginInstanceId())));
        ordered.verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof ReliableOutboxCommittedEvent committed
                        && committed.eventId() != null
                        && committed.eventId().equals(committed.envelope().getEventId())
        ));
    }
}
