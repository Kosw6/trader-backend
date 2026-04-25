package com.example.trader.dto.canvas;

import java.util.List;
import java.util.Map;

/**
 * 노드 편집 충돌 검증 결과.
 *
 * PASS       - 버전 변경 없음 또는 baseVersion 미제공 → 그냥 저장
 * AUTO_MERGE - 버전은 올랐지만 충돌 필드 없음 → 그냥 저장
 * CONFLICT   - 같은 필드를 다른 유저가 먼저 수정 → 409 반환 후 A가 최종 결정
 */
public record ConflictResult(
        ConflictType type,
        int baseVersion,
        int currentVersion,
        List<String> conflictingFields,       // CONFLICT 시에만 유의미
        Map<String, Object> currentValues,    // DB 현재 값 (CONFLICT 시 diff용)
        Map<String, Object> incomingValues    // A가 보낸 값  (CONFLICT 시 diff용)
) {
    public enum ConflictType {
        PASS,
        AUTO_MERGE,
        CONFLICT
    }

    public static ConflictResult pass(int version) {
        return new ConflictResult(ConflictType.PASS, version, version,
                List.of(), Map.of(), Map.of());
    }

    public static ConflictResult autoMerge(int base, int current) {
        return new ConflictResult(ConflictType.AUTO_MERGE, base, current,
                List.of(), Map.of(), Map.of());
    }

    public static ConflictResult conflict(int base, int current,
                                          List<String> conflictingFields,
                                          Map<String, Object> currentValues,
                                          Map<String, Object> incomingValues) {
        return new ConflictResult(ConflictType.CONFLICT, base, current,
                conflictingFields, currentValues, incomingValues);
    }

    public boolean isConflict() {
        return type == ConflictType.CONFLICT;
    }
}
