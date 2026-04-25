package com.example.trader.ws.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresenceBatch(
        String type,          // "PRESENCE_BATCH"
        Long teamId,
        Long graphId,
        long batchSentAt,
        java.util.List<CursorItem> cursors,   // 마우스 커서 위치
        java.util.List<DragItem>   dragItems  // 노드 드래그 미리보기
) {
    public record CursorItem(
            Long userId,
            String nickName,
            double x,
            double y,
            long sentAt
    ) {}

    public record DragItem(
            Long userId,
            Long nodeId,
            double x,
            double y,
            long sentAt
    ) {}
}
