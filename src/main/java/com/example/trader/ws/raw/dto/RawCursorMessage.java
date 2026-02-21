package com.example.trader.ws.raw.dto;

public record RawCursorMessage(
        String type,      // "cursor"
        Long teamId,
        Long graphId,
        Long userId,      // 서버가 세팅해도 됨
        String username,  // 서버가 세팅해도 됨
        double x,
        double y,
        long sentAt
) {}
