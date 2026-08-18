package com.example.trader.dto.canvas;

import java.util.List;

/**
 * 노드 편집 Draft 자동 저장 요청.
 *
 * @param baseVersion 편집을 시작할 때 사용자가 본 노드 버전
 * @param draftData 임시 편집 내용과 복원에 필요한 스냅샷
 * @param dirtyFields 실제로 수정한 필드 목록
 */
public record DraftAutosaveReq(
        Integer baseVersion,
        Object draftData,
        List<String> dirtyFields
) {
}
