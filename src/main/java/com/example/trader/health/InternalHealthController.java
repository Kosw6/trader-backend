package com.example.trader.health;

import com.example.trader.health.dto.HealthStatusResponse;
import com.example.trader.server.ServerStateManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalHealthController {

    private final ServerStateManager serverStateManager;

    @Value("${app.instance-id}")
    private String instanceId;

    @Value("${app.server-type:ws}")
    private String serverType;

    //현재 서버 상태 반환
    @GetMapping("/internal/health")
    public HealthStatusResponse health() {
        return HealthStatusResponse.builder()
                .instanceId(instanceId)
                .serverType(serverType)
                .up(serverStateManager.isUp())
                .ready(serverStateManager.isReady())
                .draining(serverStateManager.isDraining())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}