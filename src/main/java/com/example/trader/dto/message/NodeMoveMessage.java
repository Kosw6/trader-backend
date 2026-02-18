package com.example.trader.dto.message;

public record NodeMoveMessage(
        Long nodeId,
        double x,
        double y,
        long sentAt
) {}
