package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
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
        switch (envelope.getType()) {
            case RELIABLE -> reliablePublisher.publish(envelope);
            case VOLATILE -> volatilePublisher.publish(envelope);
            default -> throw new IllegalArgumentException("Unknown realtime type: " + envelope.getType());
        }
    }
}