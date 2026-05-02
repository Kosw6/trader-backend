package com.example.trader.infra.http.health;

import com.example.trader.realtime.health.HttpHealthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.http.enabled", havingValue = "true")
public class DefaultHttpHealthChecker implements HttpHealthChecker {

    private final RestClient restClient;

    @Value("${realtime.http.health-url:http://localhost:8081/internal/health}")
    private String healthUrl;

    @Override
    public boolean isAvailable() {
        try {
            restClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (Exception e) {
            log.warn("[HTTP] realtime health check failed: {}", e.getMessage());
            return false;
        }
    }
}