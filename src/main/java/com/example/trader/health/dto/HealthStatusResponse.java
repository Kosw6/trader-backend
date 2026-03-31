package com.example.trader.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HealthStatusResponse {
    private String instanceId;
    private String serverType;
    private boolean up;
    private boolean ready;
    private boolean draining;
    private long timestamp;
}
