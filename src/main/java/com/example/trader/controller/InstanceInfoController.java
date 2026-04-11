package com.example.trader.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

//@RestController
public class InstanceInfoController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private String serverPort;

    @Value("${APP_ROLE:unknown}")
    private String appRole;

    @Value("${APP_INSTANCE_ID:unknown}")
    private String instanceId;

    @Value("${APP_SHARD_INDEX:-1}")
    private String shardIndex;

    @GetMapping("/internal/instance-info")
    public InstanceInfoResponse instanceInfo() {
        return new InstanceInfoResponse(
                applicationName,
                appRole,
                instanceId,
                serverPort,
                shardIndex
        );
    }

    public record InstanceInfoResponse(
            String service,
            String role,
            String instanceId,
            String port,
            String shardIndex
    ) {}
}