package com.example.trader.realtime.publisher.degrade;

import com.example.trader.realtime.VolatilePublisher;
import com.example.trader.realtime.health.RealtimeHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.publisher.GrpcVolatilePublisher;
import com.example.trader.realtime.publisher.HttpVolatilePublisher;
import com.example.trader.realtime.publisher.RedisVolatilePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Primary
public class DegradableVolatilePublisher implements VolatilePublisher {

    private final RealtimeHealthState healthState;

    private final ObjectProvider<RedisVolatilePublisher> redisPublisher;
    private final ObjectProvider<GrpcVolatilePublisher> grpcPublisher;
    private final ObjectProvider<HttpVolatilePublisher> httpPublisher;

    @Value("${realtime.volatile.route-mode:redis}")
    private String routeMode;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();
        log.info("[VOLATILE-ROUTE] mode={}, eventId={}", routeMode, event.getEventId());
        try {
            switch (normalize(routeMode)) {
                case "redis" -> publishToRedis(event);
                case "grpc" -> publishToGrpc(event);
                case "http" -> publishToHttp(event);
                case "local" -> {
                    // local-only 테스트면 여기서는 아무것도 하지 않음
                    // 같은 서버 직접 전파는 inbound handler를 별도로 호출하는 방식으로 처리 가능
                }
                default -> throw new IllegalArgumentException("Unsupported volatile route-mode: " + routeMode);
            }
        } catch (Exception e) {
            log.debug("[VOLATILE] publish skipped. routeMode={}, eventId={}, reason={}",
                    routeMode, event.getEventId(), e.getMessage());
        }
    }

    private void publishToRedis(RealtimeEnvelope event) {
        if (!healthState.isRedisAvailable()) {
            throw new IllegalStateException("Redis unavailable");
        }

        RedisVolatilePublisher publisher = redisPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("RedisVolatilePublisher bean not available");
        }

        publisher.publish(event);
    }

    private void publishToGrpc(RealtimeEnvelope event) {
        if (!healthState.isGrpcAvailable()) {
            throw new IllegalStateException("gRPC unavailable");
        }

        GrpcVolatilePublisher publisher = grpcPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("GrpcVolatilePublisher bean not available");
        }

        publisher.publish(event);
    }

    private void publishToHttp(RealtimeEnvelope event) {
        if (!healthState.isHttpAvailable()) {
            throw new IllegalStateException("HTTP unavailable");
        }

        HttpVolatilePublisher publisher = httpPublisher.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("HttpVolatilePublisher bean not available");
        }

        publisher.publish(event);
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? "redis"
                : value.trim().toLowerCase();
    }
}