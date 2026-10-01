package com.example.trader.dto.canvas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Node 편집의 버전 비교와 사용자 검토 결과. */
public record ConflictResult(
        ConflictType type,
        int baseVersion,
        int currentVersion,
        List<String> serverChangedFields,
        List<String> requestedFields,
        List<String> effectiveFields,
        List<String> conflictingFields,
        Map<String, Object> currentValues,
        Map<String, Object> incomingValues,
        Map<String, Object> proposedValues
) {
    public enum ConflictType {
        PASS,
        AUTO_MERGE,
        MERGE_REVIEW_REQUIRED,
        CONFLICT,
        CONCURRENT_UPDATE
    }

    public static ConflictResult pass(int version) {
        return result(ConflictType.PASS, version, version, List.of(), List.of(),
                List.of(), List.of(), Map.of(), Map.of(), Map.of());
    }

    /** 위치처럼 명시적으로 독립된 변경 영역에서만 내부적으로 사용한다. */
    public static ConflictResult autoMerge(int base, int current) {
        return result(ConflictType.AUTO_MERGE, base, current, List.of(), List.of(),
                List.of(), List.of(), Map.of(), Map.of(), Map.of());
    }

    public static ConflictResult mergeReviewRequired(
            int base,
            int current,
            List<String> serverChangedFields,
            List<String> requestedFields,
            List<String> effectiveFields,
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues,
            Map<String, Object> proposedValues
    ) {
        return result(ConflictType.MERGE_REVIEW_REQUIRED, base, current,
                serverChangedFields, requestedFields, effectiveFields, List.of(),
                currentValues, incomingValues, proposedValues);
    }

    public static ConflictResult conflict(
            int base,
            int current,
            List<String> serverChangedFields,
            List<String> requestedFields,
            List<String> effectiveFields,
            List<String> conflictingFields,
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues,
            Map<String, Object> proposedValues
    ) {
        return result(ConflictType.CONFLICT, base, current, serverChangedFields,
                requestedFields, effectiveFields, conflictingFields,
                currentValues, incomingValues, proposedValues);
    }

    /** 위치 변경과 기존 호출자를 위한 축약 팩토리. */
    public static ConflictResult conflict(
            int base,
            int current,
            List<String> conflictingFields,
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues
    ) {
        return conflict(base, current, conflictingFields, conflictingFields,
                conflictingFields, conflictingFields, currentValues, incomingValues,
                merge(currentValues, incomingValues));
    }

    public static ConflictResult concurrentUpdate(
            int base,
            int current,
            List<String> fieldsToReview,
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues
    ) {
        return result(ConflictType.CONCURRENT_UPDATE, base, current, List.of(),
                fieldsToReview, fieldsToReview, fieldsToReview,
                currentValues, incomingValues, merge(currentValues, incomingValues));
    }

    public boolean requiresUserReview() {
        return type == ConflictType.MERGE_REVIEW_REQUIRED
                || type == ConflictType.CONFLICT
                || type == ConflictType.CONCURRENT_UPDATE;
    }

    public boolean isConflict() {
        return type == ConflictType.CONFLICT || type == ConflictType.CONCURRENT_UPDATE;
    }

    private static ConflictResult result(
            ConflictType type,
            int base,
            int current,
            List<String> serverChangedFields,
            List<String> requestedFields,
            List<String> effectiveFields,
            List<String> conflictingFields,
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues,
            Map<String, Object> proposedValues
    ) {
        return new ConflictResult(type, base, current,
                List.copyOf(serverChangedFields), List.copyOf(requestedFields),
                List.copyOf(effectiveFields), List.copyOf(conflictingFields),
                immutableCopy(currentValues), immutableCopy(incomingValues),
                immutableCopy(proposedValues));
    }

    private static Map<String, Object> merge(
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues
    ) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(currentValues);
        merged.putAll(incomingValues);
        return merged;
    }

    /** Map.copyOf는 symb=null을 허용하지 않으므로 null 값을 보존한다. */
    private static Map<String, Object> immutableCopy(Map<String, Object> values) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
