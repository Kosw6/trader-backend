package com.example.trader.realtime;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RealtimeHealthState {

    private final AtomicBoolean kafkaAvailable = new AtomicBoolean(false);
    private final AtomicBoolean grpcAvailable  = new AtomicBoolean(false);
    private final AtomicBoolean httpAvailable  = new AtomicBoolean(false);
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);

    private final MeterRegistry meterRegistry;

    public RealtimeHealthState(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("realtime_health_redis", redisAvailable, b -> b.get() ? 1.0 : 0.0)
             .description("Redis 연결 상태 (1=UP, 0=DOWN)")
             .register(meterRegistry);
        Gauge.builder("realtime_health_kafka", kafkaAvailable, b -> b.get() ? 1.0 : 0.0)
             .description("Kafka 연결 상태 (1=UP, 0=DOWN)")
             .register(meterRegistry);
        Gauge.builder("realtime_health_grpc", grpcAvailable, b -> b.get() ? 1.0 : 0.0)
             .description("gRPC relay 상태 (1=UP, 0=DOWN)")
             .register(meterRegistry);
        Gauge.builder("realtime_health_http", httpAvailable, b -> b.get() ? 1.0 : 0.0)
             .description("HTTP relay 상태 (1=UP, 0=DOWN)")
             .register(meterRegistry);
    }

    public boolean isKafkaAvailable() {
        return kafkaAvailable.get();
    }

    public boolean isGrpcAvailable() {
        return grpcAvailable.get();
    }

    public boolean isHttpAvailable() {
        return httpAvailable.get();
    }

    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }

    public void setKafkaAvailable(boolean available) {
        kafkaAvailable.set(available);
    }

    public void setGrpcAvailable(boolean available) {
        grpcAvailable.set(available);
    }

    public void setHttpAvailable(boolean available) {
        httpAvailable.set(available);
    }

    public void setRedisAvailable(boolean available) {
        redisAvailable.set(available);
    }
}