package com.example.trader.ws.smtop.dto;

public record CursorMessage(
        Long teamId,
        Long graphId,
        Long userId,
        String username,
        double x,
        double y,
        long sentAt
) {}