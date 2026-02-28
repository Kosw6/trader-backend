package com.example.trader.ws.common;

public record PresenceBatch(
        String type,          // "PRESENCE_BATCH"
        Long teamId,
        Long graphId,
        long batchSentAt,
        java.util.List<CursorItem> cursors
) {
    public record CursorItem(
            Long userId,
            String nickName,
            double x,
            double y,
            long sentAt
    ) {}
}
