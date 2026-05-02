package com.example.trader.infra.kafka.health;

import com.example.trader.realtime.health.KafkaHealthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class DefaultKafkaHealthChecker implements KafkaHealthChecker {

    private final KafkaTemplate<String, ?> kafkaTemplate;

    @Value("${realtime.kafka.topic:canvas-events}")
    private String topic;

    @Override
    public boolean isAvailable() {
        try (Producer<String, ?> producer =
                     kafkaTemplate.getProducerFactory().createProducer()) {

            producer.partitionsFor(topic);
            return true;

        } catch (Exception e) {
            log.warn("[KAFKA] health check failed: {}", e.getMessage());
            return false;
        }
    }
}