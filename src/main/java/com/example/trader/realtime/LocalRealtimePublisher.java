package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeLatestKeyResolver;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.realtime.snapshot.TransientRoomStateStore;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
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
public class LocalRealtimePublisher implements LiveRealtimePublisher {

    private final RawPresenceBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final RealtimeLatestKeyResolver latestKeyResolver;
    private final TransientRoomStateStore transientStateStore;

    @Override
    public void publishTransient(RealtimeEnvelope envelope) {
        requireType(envelope, RealtimeType.TRANSIENT);
        RealtimeEnvelope event = transientStateStore.record(envelope.ensureEventId());
        String roomKey = "team:%d:graph:%d".formatted(
                event.getTeamId(),
                event.getGraphId()
        );
        broadcaster.enqueueTransient(roomKey, event);
    }

    @Override
    public void publishVolatile(RealtimeEnvelope envelope) {
        requireType(envelope, RealtimeType.VOLATILE);
        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );
        RawCursorMessage msg = objectMapper.convertValue(
                envelope.getPayload(),
                RawCursorMessage.class
        );
        broadcaster.publishLatest(roomKey, latestKeyResolver.resolve(msg), msg);
    }

    private void requireType(RealtimeEnvelope envelope, RealtimeType expected) {
        if (envelope.getType() != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " event but got " + envelope.getType()
            );
        }
    }
}
