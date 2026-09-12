package com.example.trader.exception;

public enum NodeHistoryCorruptionReason {
    MISSING_CHANGED_FIELDS("변경 필드 정보가 없습니다."),
    MALFORMED_CHANGED_FIELDS("변경 필드 정보가 올바른 JSON 배열이 아닙니다."),
    EMPTY_CHANGED_FIELDS("변경 필드 배열이 비어 있습니다."),
    UNSUPPORTED_CHANGED_FIELD("지원하지 않는 변경 필드가 포함되어 있습니다."),
    DUPLICATE_CHANGED_FIELD("중복된 변경 필드가 포함되어 있습니다.");

    private final String message;

    NodeHistoryCorruptionReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
