package com.example.trader.infra.grpc.health;

import com.example.trader.realtime.health.GrpcHealthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.grpc.enabled", havingValue = "true")
public class DefaultGrpcHealthChecker implements GrpcHealthChecker {

    private final GrpcHealthClient grpcHealthClient;

    @Override
    public boolean isAvailable() {
        try {
            return grpcHealthClient.check();
        } catch (Exception e) {
            log.warn("[gRPC] health check failed: {}", e.getMessage());
            return false;
        }
    }
}