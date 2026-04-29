package com.example.trader.infra.kafka;

import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

//멀티에서만 등록
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "realtime.kafka.enabled",
        havingValue = "true"
)
public class MultiRealtimePublisher implements RealtimePublisher {

    private final KafkaTemplate<String, RealtimeEnvelope> kafkaTemplate;

    @Value("${realtime.kafka.topic:canvas-events}")
    private String topic;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        String key = envelope.getGraphId() == null
                ? "default"
                : envelope.getGraphId().toString();

        kafkaTemplate.send(topic, key, envelope);
    }
}