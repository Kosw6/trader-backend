package com.example.trader.ws.raw.event;

import com.example.trader.ws.raw.RawPresenceBroadcaster;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeChangedEventListener {

    private final RawPresenceBroadcaster broadcaster;

    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void handle(NodeChangedEvent event) {
        String roomKey = event.teamId() + ":" + event.graphId();

        RawCursorMessage msg = new RawCursorMessage(
                "__CONTROL__",
                event.subType(),
                event.teamId(),
                event.graphId(),
                event.userId(),
                null,
                event.nodeId(),
                event.x() != null ? event.x() : 0,
                event.y() != null ? event.y() : 0,
                System.currentTimeMillis(),
                event.fields(),
                event.version()
        );

        broadcaster.publishReliable(roomKey, msg);

        log.debug("[NODE_EVENT] broadcast queued roomKey={} subType={} nodeId={}",
                roomKey, event.subType(), event.nodeId());
    }
}
