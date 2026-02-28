package com.example.trader.ws.smtop.dto;

public record CursorMessage(
        String type, Long teamId,
        Long graphId,
        Long userId,
        String nickName,
        double x,
        double y,
        long sentAt
) {}