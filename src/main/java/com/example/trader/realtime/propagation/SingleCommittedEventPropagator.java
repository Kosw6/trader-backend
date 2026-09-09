package com.example.trader.realtime.propagation;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "single", matchIfMissing = true)
public class SingleCommittedEventPropagator implements CommittedEventPropagator {

    private final RawPresenceBroadcaster broadcaster;

    @Override
    public void propagate(RealtimeEnvelope envelope) {
        try {
            broadcaster.enqueueOrdered(roomKey(envelope), envelope);
        } catch (Exception e) {
            log.warn("[REALTIME-LOCAL-AFTER-COMMIT-FAILED] eventId={}, reason={}",
                    envelope.getEventId(), e.getMessage());
        }
    }

    private String roomKey(RealtimeEnvelope envelope) {
        return "team:%d:graph:%d".formatted(envelope.getTeamId(), envelope.getGraphId());
    }
}
