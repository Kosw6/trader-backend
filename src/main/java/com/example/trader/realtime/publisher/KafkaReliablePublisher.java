package com.example.trader.realtime.publisher;

import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaReliablePublisher implements ReliablePublisher {

    private final KafkaTemplate<String, RealtimeEnvelope> kafkaTemplate;

    @Value("${realtime.kafka.topic:canvas-events}")
    private String topic;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        try {
            publishOrThrow(envelope);
        } catch (Exception e) {
            log.warn("[KAFKA] publish failed", e);
        }
    }

    public void publishOrThrow(RealtimeEnvelope envelope) throws Exception {
        String key = envelope.getGraphId() == null
                ? "default"
                : envelope.getGraphId().toString();

        kafkaTemplate.send(topic, key, envelope).get(1, TimeUnit.SECONDS);
    }
}