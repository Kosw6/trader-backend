package com.example.trader.dto.canvas;

import java.util.List;

/**
 * Redis에 저장되는 편집 세션 정보.
 * EDIT_START WS 이벤트 수신 시 생성, EDIT_END 또는 저장 완료 시 삭제.
 *
 * key: canvas:editing:{teamId}:{graphId}:{nodeId}:{userId}
 */
public record EditSessionDto(
        int baseVersion,
        List<String> fields,
        Object draftData
) {
    public EditSessionDto(int baseVersion, List<String> fields) {
        this(baseVersion, fields, null);
    }
}