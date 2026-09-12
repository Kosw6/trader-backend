package com.example.trader.entity;

import java.util.Arrays;
import java.util.Optional;

public enum NodeContentField {
    SUBJECT("subject"),
    CONTENT("content"),
    SYMBOL("symb"),
    RECORD_DATE("recordDate"),
    NOTE_IDS("noteIds");

    private final String externalName;

    NodeContentField(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static Optional<NodeContentField> fromExternalName(String name) {
        return Arrays.stream(values())
                .filter(field -> field.externalName.equals(name))
                .findFirst();
    }
}
