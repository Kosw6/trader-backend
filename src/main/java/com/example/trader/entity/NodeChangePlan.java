package com.example.trader.entity;

import java.util.List;

public record NodeChangePlan(
        List<String> requestedFields,
        NodeContentChange requestedChange,
        NodeContentChange effectiveChange
) {
    public NodeChangePlan {
        requestedFields = List.copyOf(requestedFields);
    }

    public boolean isNoOp() {
        return effectiveChange.isEmpty();
    }
}
