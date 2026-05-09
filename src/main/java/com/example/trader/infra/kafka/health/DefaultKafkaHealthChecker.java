package com.example.trader.infra.kafka.health;

import com.example.trader.realtime.health.KafkaHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class DefaultKafkaHealthChecker implements KafkaHealthChecker, DisposableBean {

    private final AdminClient adminClient;
    private final String topic;
    private final long timeoutMs;

    public DefaultKafkaHealthChecker(
            KafkaTemplate<String, ?> kafkaTemplate,
            @Value("${realtime.kafka.topic:canvas-events}") String topic,
            @Value("${realtime.kafka.health-check.timeout-ms:2000}") long timeoutMs
    ) {
        this.topic = topic;
        this.timeoutMs = timeoutMs;

        Map<String, Object> adminProps = new HashMap<>(
                kafkaTemplate.getProducerFactory().getConfigurationProperties()
        );
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) timeoutMs);
        adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) timeoutMs);

        this.adminClient = AdminClient.create(adminProps);
    }

    @Override
    public boolean isAvailable() {
        try {
            adminClient.describeTopics(List.of(topic))
                    .allTopicNames()
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.warn("[KAFKA] health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void destroy() {
        try {
            adminClient.close(Duration.ofSeconds(3));
        } catch (Exception ignored) {
        }
    }
}
