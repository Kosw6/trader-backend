package com.example.trader.realtime.inbound;

import com.example.trader.realtime.LocalReliableEventDeduplicator;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReliableInboundHandler {

    private final RawPresenceBroadcaster rawPresenceBroadcaster;
    private final LocalReliableEventDeduplicator deduplicator;
    private final com.example.trader.ws.raw.TeamTreeHub teamTreeHub;

    /**
     * RELIABLE 이벤트의 모든 로컬 수신 경로에서 공통으로 사용한다.
     * eventId를 먼저 원자적으로 선점하며, 큐 적재 실패 시 선점을 해제해 다른 경로의 복구를 허용한다.
     */
    public boolean handle(RealtimeEnvelope envelope) {
        boolean treeEvent = envelope.getSubType() != null && envelope.getSubType().isTeamScoped();
        if (treeEvent && (envelope.getType() != com.example.trader.realtime.message.RealtimeType.RELIABLE
                || envelope.getGraphId() != null || envelope.getTeamId() == null || envelope.getTeamId() <= 0)) {
            throw new IllegalArgumentException("Invalid team tree event scope");
        }
        if (!deduplicator.tryClaim(envelope.getEventId())) {
            log.debug("[RELIABLE-INBOUND] duplicate skipped. eventId={}", envelope.getEventId());
            return false;
        }

        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );
        try {
            if (treeEvent) {
                teamTreeHub.publish(envelope);
                return true;
            }
            log.info("[RELIABLE-INBOUND] roomKey={}, eventId={}", roomKey, envelope.getEventId());
            rawPresenceBroadcaster.enqueueReliable(roomKey, envelope);
            return true;
        } catch (RuntimeException e) {
            deduplicator.release(envelope.getEventId());
            throw e;
        }
    }
}
