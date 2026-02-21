package com.example.trader.ws.smtop.dto;

public record NodeMoveMessage(
        Long nodeId,
        double x,
        double y,
        long sentAt
) {}
