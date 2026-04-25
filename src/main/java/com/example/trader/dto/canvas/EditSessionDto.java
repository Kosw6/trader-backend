package com.example.trader.dto.canvas;

import java.util.List;

/**
 * Redis에 저장되는 편집 세션 정보.
 * EDIT_START WS 이벤트 수신 시 생성, EDIT_END 또는 저장 완료 시 삭제.
 *
 * key: canvas:editing:{teamId}:{graphId}:{nodeId}:{userId}
 */
public record EditSessionDto(
        int baseVersion,       // A가 편집을 시작할 당시의 노드 버전
        List<String> fields    // A가 편집 중인 필드명 목록 ex) ["subject", "content"]
) {}
