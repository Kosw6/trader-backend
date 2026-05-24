package com.example.trader.realtime.publisher;

import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class KafkaReliablePublisher{//DegradableReliablePublisher의 하위 구현체

    private final KafkaTemplate<String, RealtimeEnvelope> kafkaTemplate;

    @Value("${realtime.kafka.topic:canvas-events}")
    private String topic;

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

        // publishedAt이 없으면 kafkaTemplate.send 직전에 세팅
        // envelope 생성 시점이 아닌 실제 발행 시점 기준으로 lag 측정
        RealtimeEnvelope stamped = envelope.getPublishedAt() != null
                ? envelope
                : envelope.toBuilder().publishedAt(System.currentTimeMillis()).build();

        kafkaTemplate.send(topic, key, stamped).get(1, TimeUnit.SECONDS);
    }
}