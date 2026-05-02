package com.example.trader.infra.http.health;

import com.example.trader.realtime.health.HttpHealthChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "realtime.http.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopHttpHealthChecker implements HttpHealthChecker {

    @Override
    public boolean isAvailable() {
        return false;
    }
}