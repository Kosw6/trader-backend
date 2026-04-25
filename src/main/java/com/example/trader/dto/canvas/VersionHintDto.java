package com.example.trader.dto.canvas;

import java.util.List;

/**
 * Redis에 저장되는 버전별 변경 힌트.
 * 노드 저장 성공 시 NodeService에서 기록.
 *
 * key: canvas:version-hint:{teamId}:{graphId}:{nodeId}:{version}
 * TTL: 1시간
 */
public record VersionHintDto(
        int version,
        List<String> changedFields,   // 이 버전에서 변경된 필드명 목록
        Long changedBy,               // 변경한 userId
        long changedAt                // epoch millis
) {}
