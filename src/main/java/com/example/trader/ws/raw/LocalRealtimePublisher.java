package com.example.trader.ws.raw;

import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeLatestKeyResolver;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.mode",
        havingValue = "single",
        matchIfMissing = true
)
public class LocalRealtimePublisher implements RealtimePublisher {

    private final RawPresenceBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final RealtimeLatestKeyResolver latestKeyResolver;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );

        if (envelope.getType() == RealtimeType.RELIABLE) {
            broadcaster.publishReliable(roomKey, envelope);
            return;
        }

        if (envelope.getType() == RealtimeType.VOLATILE) {
            RawCursorMessage msg = objectMapper.convertValue(
                    envelope.getPayload(),
                    RawCursorMessage.class
            );

            String latestKey = latestKeyResolver.resolve(msg);
            broadcaster.publishLatest(roomKey, latestKey, msg);
            return;
        }

        throw new IllegalArgumentException("Unsupported realtime type: " + envelope.getType());
    }
}