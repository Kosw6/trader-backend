package com.example.trader.entity;

import java.util.List;

public record NodeChangeResult(
        int previousVersion,
        int newVersion,
        List<String> appliedFields
) {
    public NodeChangeResult {
        appliedFields = List.copyOf(appliedFields);
    }

    public boolean changed() {
        return !appliedFields.isEmpty();
    }
}
