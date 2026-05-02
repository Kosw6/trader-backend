package com.example.trader.infra.kafka.health;

import com.example.trader.realtime.health.KafkaHealthChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "realtime.kafka.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopKafkaHealthChecker implements KafkaHealthChecker {//Kafka 비활성화용

    @Override
    public boolean isAvailable() {
        return false;
    }
}