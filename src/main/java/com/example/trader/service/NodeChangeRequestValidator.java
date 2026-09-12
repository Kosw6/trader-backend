package com.example.trader.service;

import com.example.trader.dto.map.NodeContentUpdateRequest;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeChangePlan;
import com.example.trader.entity.NodeContentChange;
import com.example.trader.entity.NodeContentField;
import com.example.trader.exception.InvalidNodeChangeRequestException;
import com.example.trader.exception.NodeChangeErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class NodeChangeRequestValidator {

    public NodeChangePlan validatePersonal(
            NodeContentUpdateRequest request,
            Node node,
            Set<Long> currentNoteIds
    ) {
        if (request.getUnsupportedValues().containsKey("dirtyFields")) {
            throw error(NodeChangeErrorCode.DIRTY_FIELDS_NOT_ALLOWED);
        }
        rejectUnsupportedBodyValues(request);

        List<NodeContentField> requestedFields = suppliedFields(request);
        if (requestedFields.isEmpty()) {
            throw error(NodeChangeErrorCode.CHANGE_FIELDS_REQUIRED);
        }
        return createPlan(request, node, currentNoteIds, requestedFields);
    }

    public NodeChangePlan validateTeam(
            UpdateTeamNodeContentRequest request,
            Node node,
            Set<Long> currentNoteIds
    ) {
        if (request.getBaseVersion() == null) {
            throw error(NodeChangeErrorCode.BASE_VERSION_REQUIRED);
        }
        if (request.getBaseVersion() < 0) {
            throw error(NodeChangeErrorCode.INVALID_BASE_VERSION);
        }
        return validateTeamContract(request, node, currentNoteIds);
    }

    private NodeChangePlan validateTeamContract(
            UpdateTeamNodeContentRequest request,
            Node node,
            Set<Long> currentNoteIds
    ) {
        rejectUnsupportedBodyValues(request);

        List<String> dirtyFields = request.getDirtyFields();
        if (dirtyFields == null) {
            throw new InvalidNodeChangeRequestException(NodeChangeErrorCode.DIRTY_FIELDS_REQUIRED);
        }
        if (dirtyFields.isEmpty()) {
            throw error(NodeChangeErrorCode.DIRTY_FIELDS_EMPTY);
        }

        Set<String> uniqueNames = new LinkedHashSet<>();
        List<NodeContentField> declaredFields = new ArrayList<>();
        for (String name : dirtyFields) {
            if (name == null) {
                throw error(NodeChangeErrorCode.UNSUPPORTED_DIRTY_FIELD);
            }
            if (!uniqueNames.add(name)) {
                throw new InvalidNodeChangeRequestException(
                        NodeChangeErrorCode.DUPLICATE_DIRTY_FIELD,
                        "field=" + name
                );
            }
            if ("x".equals(name) || "y".equals(name)) {
                throw error(NodeChangeErrorCode.POSITION_FIELD_NOT_ALLOWED);
            }
            declaredFields.add(NodeContentField.fromExternalName(name)
                    .orElseThrow(() -> new InvalidNodeChangeRequestException(
                            NodeChangeErrorCode.UNSUPPORTED_DIRTY_FIELD,
                            "field=" + name
                    )));
        }

        for (NodeContentField field : NodeContentField.values()) {
            boolean declared = uniqueNames.contains(field.externalName());
            boolean supplied = request.isFieldSupplied(field.externalName());
            if (declared && !supplied) {
                throw new InvalidNodeChangeRequestException(
                        NodeChangeErrorCode.DIRTY_FIELD_VALUE_MISSING,
                        "field=" + field.externalName()
                );
            }
            if (!declared && supplied) {
                throw new InvalidNodeChangeRequestException(
                        NodeChangeErrorCode.UNDECLARED_FIELD_VALUE,
                        "field=" + field.externalName()
                );
            }
        }

        return createPlan(request, node, currentNoteIds, declaredFields);
    }

    private List<NodeContentField> suppliedFields(NodeContentUpdateRequest request) {
        List<NodeContentField> suppliedFields = new ArrayList<>();
        for (NodeContentField field : NodeContentField.values()) {
            if (request.isFieldSupplied(field.externalName())) {
                suppliedFields.add(field);
            }
        }
        return suppliedFields;
    }

    private NodeChangePlan createPlan(
            NodeContentUpdateRequest request,
            Node node,
            Set<Long> currentNoteIds,
            List<NodeContentField> requestedFields
    ) {
        EnumMap<NodeContentField, Object> normalized = new EnumMap<>(NodeContentField.class);
        for (NodeContentField field : requestedFields) {
            Object value = normalizeAndValidate(field, request.valueOf(field.externalName()));
            normalized.put(field, value);
        }

        EnumMap<NodeContentField, Object> effective = new EnumMap<>(NodeContentField.class);
        for (Map.Entry<NodeContentField, Object> entry : normalized.entrySet()) {
            if (!sameAsCurrent(node, currentNoteIds, entry.getKey(), entry.getValue())) {
                effective.put(entry.getKey(), entry.getValue());
            }
        }

        return new NodeChangePlan(
                requestedFields.stream().map(NodeContentField::externalName).toList(),
                NodeContentChange.of(normalized),
                NodeContentChange.of(effective)
        );
    }

    private void rejectUnsupportedBodyValues(NodeContentUpdateRequest request) {
        if (request.getUnsupportedValues().containsKey("x")
                || request.getUnsupportedValues().containsKey("y")) {
            throw error(NodeChangeErrorCode.POSITION_FIELD_NOT_ALLOWED);
        }
        if (!request.getUnsupportedValues().isEmpty()) {
            throw new InvalidNodeChangeRequestException(
                    NodeChangeErrorCode.UNDECLARED_FIELD_VALUE,
                    "field=" + request.getUnsupportedValues().keySet().iterator().next()
            );
        }
    }

    private Object normalizeAndValidate(NodeContentField field, Object value) {
        return switch (field) {
            case SUBJECT -> {
                if (value == null) throw error(NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED);
                if (((String) value).isEmpty()) throw error(NodeChangeErrorCode.EMPTY_SUBJECT_NOT_ALLOWED);
                yield value;
            }
            case CONTENT, RECORD_DATE -> {
                if (value == null) throw error(NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED);
                yield value;
            }
            case SYMBOL -> {
                if (value == null) throw error(NodeChangeErrorCode.NULL_VALUE_NOT_ALLOWED);
                String symbol = (String) value;
                yield symbol.isEmpty() ? null : symbol;
            }
            case NOTE_IDS -> {
                if (value == null) throw error(NodeChangeErrorCode.DIRTY_FIELD_VALUE_MISSING);
                yield List.copyOf((List<?>) value);
            }
        };
    }

    private boolean sameAsCurrent(
            Node node,
            Set<Long> currentNoteIds,
            NodeContentField field,
            Object requestedValue
    ) {
        Object currentValue = switch (field) {
            case SUBJECT -> node.getSubject();
            case CONTENT -> node.getContent();
            case SYMBOL -> node.getSymb();
            case RECORD_DATE -> node.getRecordDate();
            case NOTE_IDS -> new HashSet<>(currentNoteIds);
        };
        Object comparableRequested = field == NodeContentField.NOTE_IDS
                ? new HashSet<>((List<?>) requestedValue)
                : requestedValue;
        return Objects.equals(currentValue, comparableRequested);
    }

    private InvalidNodeChangeRequestException error(NodeChangeErrorCode errorCode) {
        return new InvalidNodeChangeRequestException(errorCode);
    }
}
