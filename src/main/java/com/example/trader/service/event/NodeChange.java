package com.example.trader.service.event;

import com.example.trader.entity.NodeContentField;
import java.util.HashSet;
import java.util.List;

/** 전송 DTO와 무관한 Node 변경 사실. 변경 필드는 실제 적용 결과이다. */
public record NodeChange(
        Kind kind, Long teamId, Long graphId, Long nodeId, Long userId,
        List<String> fields, Integer version, Double x, Double y
) {
    public enum Kind { CREATED, UPDATED, DELETED, POSITION_UPDATED }

    public NodeChange {
        if (kind == null || teamId == null || graphId == null || nodeId == null) {
            throw new IllegalArgumentException("변경 종류와 팀·그래프·노드 식별자는 필수입니다.");
        }
        if (fields == null || fields.isEmpty() || fields.stream().anyMatch(f -> f == null)
                || new HashSet<>(fields).size() != fields.size()) {
            throw new IllegalArgumentException("변경 필드는 비어 있거나 중복될 수 없습니다.");
        }
        fields = List.copyOf(fields);
        if (kind != Kind.DELETED && (userId == null || version == null || version < 0)) {
            throw new IllegalArgumentException("생성·수정에는 행위자와 유효한 업무 버전이 필요합니다.");
        }
        if (kind == Kind.DELETED && (userId != null || version != null)) {
            throw new IllegalArgumentException("현재 삭제 이벤트는 행위자와 버전을 제공하지 않습니다.");
        }
        switch (kind) {
            case CREATED, DELETED -> {
                if (!fields.equals(List.of("node"))) {
                    throw new IllegalArgumentException("생성·삭제의 변경 대상은 node입니다.");
                }
            }
            case UPDATED -> {
                if (fields.stream().anyMatch(f -> NodeContentField.fromExternalName(f).isEmpty())) {
                    throw new IllegalArgumentException("내용 변경에는 지원하는 내용 필드만 포함할 수 있습니다.");
                }
            }
            case POSITION_UPDATED -> {
                if (!fields.equals(List.of("x", "y")) || x == null || y == null
                        || !Double.isFinite(x) || !Double.isFinite(y)) {
                    throw new IllegalArgumentException("위치 확정에는 x·y 변경 필드와 유한한 좌표가 필요합니다.");
                }
            }
        }
        if (kind != Kind.POSITION_UPDATED && (x != null || y != null)) {
            throw new IllegalArgumentException("위치 확정 이외의 이벤트에는 좌표를 지정할 수 없습니다.");
        }
    }

    public static NodeChange created(Long teamId, Long graphId, Long nodeId, Long userId, int version) {
        return new NodeChange(Kind.CREATED, teamId, graphId, nodeId, userId, List.of("node"), version, null, null);
    }

    public static NodeChange updated(Long teamId, Long graphId, Long nodeId, Long userId,
                                     List<String> appliedFields, int version) {
        return new NodeChange(Kind.UPDATED, teamId, graphId, nodeId, userId, appliedFields, version, null, null);
    }

    public static NodeChange deleted(Long teamId, Long graphId, Long nodeId) {
        return new NodeChange(Kind.DELETED, teamId, graphId, nodeId, null, List.of("node"), null, null, null);
    }

    public static NodeChange positionUpdated(Long teamId, Long graphId, Long nodeId, Long userId,
                                             int version, double x, double y) {
        return new NodeChange(Kind.POSITION_UPDATED, teamId, graphId, nodeId, userId,
                List.of("x", "y"), version, x, y);
    }
}
