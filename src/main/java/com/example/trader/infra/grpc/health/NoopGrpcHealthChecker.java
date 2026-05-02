package com.example.trader.infra.grpc.health;

import com.example.trader.realtime.health.GrpcHealthChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "realtime.grpc.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoopGrpcHealthChecker implements GrpcHealthChecker {

    @Override
    public boolean isAvailable() {
        return false;
    }
}