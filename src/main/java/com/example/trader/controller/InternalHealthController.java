package com.example.trader.controller;

import com.example.trader.server.ServerStateManager;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalHealthController {

    private final ServerStateManager serverStateManager;

    @Value("${INSTANCE_ID:unknown}")
    private String instanceId;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "instanceId", instanceId,
                "serverType", "ws",
                "up", serverStateManager.isUp(),
                "ready", serverStateManager.isReady(),
                "draining", serverStateManager.isDraining(),
                "timestamp", System.currentTimeMillis()
        );
    }
}
