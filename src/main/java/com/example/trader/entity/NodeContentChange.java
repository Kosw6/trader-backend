package com.example.trader.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NodeContentChange {
    private final EnumMap<NodeContentField, Object> values;

    private NodeContentChange(Map<NodeContentField, Object> values) {
        this.values = new EnumMap<>(NodeContentField.class);
        values.forEach((field, value) -> this.values.put(
                field,
                field == NodeContentField.NOTE_IDS && value != null
                        ? List.copyOf((List<?>) value)
                        : value
        ));
    }

    public static NodeContentChange of(Map<NodeContentField, Object> values) {
        return new NodeContentChange(values);
    }

    public static NodeContentChange empty() {
        return new NodeContentChange(Map.of());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean contains(NodeContentField field) {
        return values.containsKey(field);
    }

    public Object value(NodeContentField field) {
        return values.get(field);
    }

    @SuppressWarnings("unchecked")
    public List<Long> noteIds() {
        if (!contains(NodeContentField.NOTE_IDS)) {
            return null;
        }
        return Collections.unmodifiableList((List<Long>) values.get(NodeContentField.NOTE_IDS));
    }

    public List<String> fieldNames() {
        List<String> names = new ArrayList<>();
        values.keySet().forEach(field -> names.add(field.externalName()));
        return List.copyOf(names);
    }

    public Map<String, Object> externalValues() {
        Map<String, Object> external = new LinkedHashMap<>();
        values.forEach((field, value) -> external.put(field.externalName(), value));
        return Collections.unmodifiableMap(external);
    }
}
