package com.example.trader.dto.message;

public record CursorMessage(
        Long teamId,
        Long graphId,
        Long userId,
        String username,
        double x,
        double y,
        long sentAt
) {}