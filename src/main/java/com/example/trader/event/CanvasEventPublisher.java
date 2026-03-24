package com.example.trader.event;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanvasEventPublisher {

    private final KafkaTemplate<String, CanvasEventEnvelope> kafkaTemplate;

    public void publish(CanvasEventEnvelope event) {
        kafkaTemplate.send("canvas-events", String.valueOf(event.getGroupId()), event);
    }
}