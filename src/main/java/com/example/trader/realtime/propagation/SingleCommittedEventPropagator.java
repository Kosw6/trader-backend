package com.example.trader.realtime.propagation;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "single", matchIfMissing = true)
public class SingleCommittedEventPropagator implements CommittedEventPropagator {

    private final ReliableInboundHandler reliableInboundHandler;

    @Override
    public void propagate(RealtimeEnvelope envelope) {
        try {
            reliableInboundHandler.handle(envelope);
        } catch (Exception e) {
            log.warn("[REALTIME-LOCAL-AFTER-COMMIT-FAILED] eventId={}, reason={}",
                    envelope.getEventId(), e.getMessage());
        }
    }

}
