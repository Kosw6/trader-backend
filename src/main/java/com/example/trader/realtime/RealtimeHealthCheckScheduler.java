package com.example.trader.realtime;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.realtime.health.GrpcHealthChecker;
import com.example.trader.realtime.health.HttpHealthChecker;
import com.example.trader.realtime.health.KafkaHealthChecker;
import com.example.trader.realtime.health.RealtimeHealthState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeHealthCheckScheduler {

    private final RealtimeHealthState healthState;

    private final RedisHealthState redisHealthState;
    private final KafkaHealthChecker kafkaHealthChecker;
    private final GrpcHealthChecker grpcHealthChecker;
    private final HttpHealthChecker httpHealthChecker;

    @Scheduled(fixedDelayString = "${realtime.health-check.delay-ms:5000}")
    public void check() {
        boolean redis = redisHealthState.check();
        boolean kafka = kafkaHealthChecker.isAvailable();
        boolean grpc = grpcHealthChecker.isAvailable();
        boolean http = httpHealthChecker.isAvailable();

        healthState.setRedisAvailable(redis);
        healthState.setKafkaAvailable(kafka);
        healthState.setGrpcAvailable(grpc);
        healthState.setHttpAvailable(http);

        log.debug("[REALTIME-HEALTH] redis={}, kafka={}, grpc={}, http={}",
                redis, kafka, grpc, http);
    }
}