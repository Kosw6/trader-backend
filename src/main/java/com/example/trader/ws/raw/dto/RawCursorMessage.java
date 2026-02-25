package com.example.trader.ws.raw.dto;

public record RawCursorMessage(
        String type,
        Long teamId,
        Long graphId,
        Long userId,
        String nickName,
        Long nodeId,     // DRAG_PREVIEW용 (CURSOR는 null)
        double x,
        double y,
        long sentAt
) {}
