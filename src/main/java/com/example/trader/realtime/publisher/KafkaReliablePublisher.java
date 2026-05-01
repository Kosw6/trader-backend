package com.example.trader.realtime.publisher;

import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class KafkaReliablePublisher {

    private final KafkaTemplate<String, RealtimeEnvelope> kafkaTemplate;

    @Value("${realtime.kafka.topic:canvas-events}")
    private String topic;

    public void publishOrThrow(RealtimeEnvelope envelope) throws Exception {
        String key = envelope.getGraphId() == null
                ? "default"
                : envelope.getGraphId().toString();

        kafkaTemplate.send(topic, key, envelope).get(1, TimeUnit.SECONDS);
    }
}