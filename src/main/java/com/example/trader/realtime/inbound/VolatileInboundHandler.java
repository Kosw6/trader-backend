package com.example.trader.realtime.inbound;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeLatestKeyResolver;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolatileInboundHandler {

    private final ObjectMapper objectMapper;
    private final RealtimeLatestKeyResolver latestKeyResolver;
    private final RawPresenceBroadcaster rawPresenceBroadcaster;

    public void handle(RealtimeEnvelope envelope) {
        RawCursorMessage msg = objectMapper.convertValue(
                envelope.getPayload(),
                RawCursorMessage.class
        );

        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );

        String latestKey = latestKeyResolver.resolve(msg);
        log.info("[VOLATILE-INBOUND] roomKey={}, latestKey={}", roomKey, latestKey);
        rawPresenceBroadcaster.publishLatest(roomKey, latestKey, msg);
    }
}