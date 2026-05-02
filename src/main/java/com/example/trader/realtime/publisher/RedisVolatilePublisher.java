package com.example.trader.realtime.publisher;

import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.health.RealtimeHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

//휘발성 데이터는 레디스 펍/섭으로 전파
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class RedisVolatilePublisher implements ReliablePublisher.VolatilePublisher {

    private final RealtimeHealthState healthState;

    private final RedisPubSubPublisher redisPublisher;
    private final ObjectProvider<GrpcVolatilePublisher> grpcPublisher;
    private final ObjectProvider<HttpVolatilePublisher> httpPublisher;

    @Value("${realtime.volatile.route-mode:redis}")
    private String routeMode;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();
        log.info("[VOLATILE-REDIS-PUBLISH] eventId={}", envelope.getEventId());
        try {
            switch (normalize(routeMode)) {
                case "redis" -> {
                    if (!healthState.isRedisAvailable()) {
                        throw new IllegalStateException("Redis unavailable");
                    }
                    redisPublisher.publish(envelope);
                }
                case "grpc" -> {
                    if (!healthState.isGrpcAvailable()) {
                        throw new IllegalStateException("gRPC unavailable");
                    }
                    grpcPublisher.getObject().publish(event);
                }
                case "http" -> {
                    if (!healthState.isHttpAvailable()) {
                        throw new IllegalStateException("HTTP unavailable");
                    }
                    httpPublisher.getObject().publish(event);
                }
                case "local" -> {
                    // 같은 서버 내부만 처리할 때 사용
                }
                default -> throw new IllegalArgumentException("Unsupported volatile route-mode: " + routeMode);
            }
        } catch (Exception e) {
            log.debug("[VOLATILE] publish skipped. routeMode={}, eventId={}, reason={}",
                    routeMode, event.getEventId(), e.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? "redis"
                : value.trim().toLowerCase();
    }
}
