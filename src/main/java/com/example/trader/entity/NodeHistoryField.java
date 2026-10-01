package com.example.trader.entity;

import java.util.Arrays;
import java.util.Optional;

/** NodeHistory가 기록할 수 있는 전체 변경 필드 계약. */
public enum NodeHistoryField {
    SUBJECT("subject"),
    CONTENT("content"),
    SYMBOL("symb"),
    RECORD_DATE("recordDate"),
    NOTE_IDS("noteIds"),
    X("x"),
    Y("y");

    private final String externalName;

    NodeHistoryField(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static Optional<NodeHistoryField> fromExternalName(String name) {
        return Arrays.stream(values())
                .filter(field -> field.externalName.equals(name))
                .findFirst();
    }
}
