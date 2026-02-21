package com.example.trader.ws.smtop.dto;

public record LockMessage(
        Long teamId,
        Long graphId,
        Long nodeId,
        String action // "ACQUIRE" | "RELEASE"
) {}
