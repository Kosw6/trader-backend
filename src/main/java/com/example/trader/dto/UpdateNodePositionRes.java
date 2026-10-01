package com.example.trader.dto;

public record UpdateNodePositionRes(
        Long nodeId,
        double x,
        double y,
        int version,
        boolean autoMerged
) {
}
