package com.example.trader.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.trader.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalInfoController {

    private final AppProperties appProperties;

    public InternalInfoController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping("/internal/ping")
    public String ping() {
        return "pong";
    }

    @GetMapping("/internal/instance-info")
    public Map<String, Object> instanceInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", appProperties.getRole());
        result.put("instanceId", appProperties.getInstanceId());
        return result;
    }
}