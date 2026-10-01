package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.service.event.NodeChange;
import com.example.trader.service.event.NodeChangeEventStager;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class OutboxNodeChangeEventStager implements NodeChangeEventStager {
    private final TransactionalReliableEventService reliableEventService;
    private final Clock clock;

    @Autowired
    public OutboxNodeChangeEventStager(TransactionalReliableEventService reliableEventService) {
        this(reliableEventService, Clock.systemUTC());
    }

    OutboxNodeChangeEventStager(TransactionalReliableEventService reliableEventService, Clock clock) {
        this.reliableEventService = reliableEventService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(NodeChange change) {
        if (change == null) {
            throw new IllegalArgumentException("기록할 Node 변경이 필요합니다.");
        }
        RealtimeSubType subType = switch (change.kind()) {
            case CREATED -> RealtimeSubType.NODE_CREATED;
            case UPDATED -> RealtimeSubType.NODE_UPDATED;
            case DELETED -> RealtimeSubType.NODE_DELETED;
            case POSITION_UPDATED -> RealtimeSubType.NODE_POSITION_UPDATED;
        };
        RawCursorMessage message = new RawCursorMessage(
                "__CONTROL__", subType.name(), change.teamId(), change.graphId(), change.userId(),
                null, change.nodeId(), change.x() == null ? 0 : change.x(),
                change.y() == null ? 0 : change.y(), clock.millis(), change.fields(),
                null, null, change.version());
        reliableEventService.stage(RealtimeEnvelope.builder()
                .type(RealtimeType.RELIABLE).subType(subType)
                .teamId(change.teamId()).graphId(change.graphId()).payload(message).build());
    }
}
