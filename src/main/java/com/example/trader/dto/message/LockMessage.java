package com.example.trader.dto.message;

public record LockMessage(
        Long teamId,
        Long graphId,
        Long nodeId,
        String action // "ACQUIRE" | "RELEASE"
) {}
