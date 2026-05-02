package com.example.trader.realtime.inbound;

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

    public void handle(RealtimeEnvelope envelope) {

        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );
        log.info("[RELIABLE-INBOUND] roomKey={}, eventId={}", roomKey, envelope.getEventId());
        rawPresenceBroadcaster.publishReliable(roomKey, envelope);
    }
}