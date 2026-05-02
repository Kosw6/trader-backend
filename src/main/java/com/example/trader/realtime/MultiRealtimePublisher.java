package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

//멀티에서만 등록
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class MultiRealtimePublisher implements RealtimePublisher {

    private final ReliablePublisher reliablePublisher;
    private final VolatilePublisher volatilePublisher;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();
        switch (event.getType()) {
            case RELIABLE -> reliablePublisher.publish(event);
            case VOLATILE -> volatilePublisher.publish(event);
            default -> throw new IllegalArgumentException("Unknown realtime type: " + event.getType());
        }
    }
}