package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeContentChange;
import com.example.trader.entity.NodeHistory;
import com.example.trader.entity.NodeHistoryField;
import com.example.trader.exception.NodeHistoryCorruptedException;
import com.example.trader.exception.NodeHistoryCorruptionReason;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 노드 편집 충돌 검증기.
 *
 * <p>검증 흐름:
 * <ol>
 *   <li>baseVersion 없음 → PASS (구버전 클라이언트 호환)</li>
 *   <li>baseVersion == currentVersion → PASS (변경 없음)</li>
 *   <li>Redis 힌트 체인 완전 → 힌트 기반 변경 필드 집계</li>
 *   <li>Redis 힌트 불완전 → DB NodeHistory fallback</li>
 *   <li>콘텐츠의 서버 변경과 요청 변경이 겹치지 않음 → 사용자 병합 검토</li>
 *   <li>콘텐츠의 서버 변경과 요청 변경이 겹침 → 충돌 선택 요청</li>
 *   <li>위치 변경만 서로 독립적이면 → 내부 AUTO_MERGE</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeConflictValidator {

    private static final List<String> ALL_CONTENT_FIELDS = List.of(
            "subject", "content", "symb", "recordDate", "noteIds"
    );

    private final NodeEditSessionService editSessionService;
    private final NodeHistoryRepository  nodeHistoryRepository;
    private final ObjectMapper           objectMapper;
    private final MeterRegistry          meterRegistry;

    /**
     * 충돌 여부를 검증하여 {@link ConflictResult}를 반환.
     *
     * @param teamId  팀 ID
     * @param graphId 그래프 ID
     * @param nodeId  노드 ID
     * @param node    현재 DB 상태 (findByIdWithLinks 로드된 것)
     * @param requestedChange 사용자가 명시한 변경 의도
     * @param effectiveChange 현재 값과 실제로 다른 변경
     */
    public ConflictResult validateContent(
            Long teamId,
            Long graphId,
            Long nodeId,
            Node node,
            int baseVersion,
            NodeContentChange requestedChange,
            NodeContentChange effectiveChange
    ) {
        int currentVersion = node.getVersion();
        if (baseVersion == currentVersion) {
            return ConflictResult.pass(currentVersion);
        }

        List<String> requestedFields = requestedChange.fieldNames();
        List<String> effectiveFields = effectiveChange.fieldNames();
        Map<String, Object> currentValues = extractCurrentValues(node, ALL_CONTENT_FIELDS);
        Map<String, Object> incomingValues = requestedChange.externalValues();
        Map<String, Object> proposedValues = mergeValues(currentValues, incomingValues);

        // 서버가 알지 못하는 미래 버전은 비교 기준 자체가 성립하지 않는다.
        if (baseVersion > currentVersion) {
            return ConflictResult.conflict(
                    baseVersion,
                    currentVersion,
                    List.of(),
                    requestedFields,
                    effectiveFields,
                    effectiveFields,
                    currentValues,
                    incomingValues,
                    proposedValues
            );
        }

        Set<String> changedByOthers = loadChangedFields(
                teamId, graphId, nodeId, baseVersion, currentVersion, requestedFields
        );
        List<String> serverChangedFields = ALL_CONTENT_FIELDS.stream()
                .filter(changedByOthers::contains)
                .toList();

        // 버전 차이가 위치 변경에서만 생겼다면 콘텐츠 계약에는 영향이 없다.
        if (serverChangedFields.isEmpty()) {
            return ConflictResult.autoMerge(baseVersion, currentVersion);
        }

        List<String> conflictingFields = effectiveFields.stream()
                .filter(changedByOthers::contains)
                .toList();

        if (conflictingFields.isEmpty()) {
            log.info(
                    "node conflict validate result=merge_review_required teamId={} graphId={} nodeId={} baseVersion={} currentVersion={} requestedFields={} effectiveFields={} serverChangedFields={}",
                    teamId, graphId, nodeId, baseVersion, currentVersion,
                    requestedFields, effectiveFields, serverChangedFields
            );
            return ConflictResult.mergeReviewRequired(
                    baseVersion,
                    currentVersion,
                    serverChangedFields,
                    requestedFields,
                    effectiveFields,
                    currentValues,
                    incomingValues,
                    proposedValues
            );
        }

        log.warn(
                "node conflict validate result=conflict teamId={} graphId={} nodeId={} baseVersion={} currentVersion={} conflictingFields={}",
                teamId, graphId, nodeId, baseVersion, currentVersion, conflictingFields
        );
        return ConflictResult.conflict(
                baseVersion,
                currentVersion,
                serverChangedFields,
                requestedFields,
                effectiveFields,
                conflictingFields,
                currentValues,
                incomingValues,
                proposedValues
        );
    }

    public ConflictResult concurrentContentUpdate(
            Node node,
            int baseVersion,
            NodeContentChange requestedChange
    ) {
        return ConflictResult.concurrentUpdate(
                baseVersion,
                node.getVersion(),
                requestedChange.fieldNames(),
                extractCurrentValues(node, ALL_CONTENT_FIELDS),
                requestedChange.externalValues()
        );
    }

    public ConflictResult validatePosition(
            Long teamId,
            Long graphId,
            Long nodeId,
            Node node,
            int baseVersion,
            double x,
            double y
    ) {
        return validateFields(
                teamId,
                graphId,
                nodeId,
                baseVersion,
                node.getVersion(),
                List.of("x", "y"),
                Map.of("x", node.getX(), "y", node.getY()),
                Map.of("x", x, "y", y)
        );
    }

    private ConflictResult validateFields(
            Long teamId,
            Long graphId,
            Long nodeId,
            int baseVersion,
            int currentVersion,
            List<String> incomingFields,
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues
    ) {
        if (baseVersion == currentVersion) {
            return ConflictResult.pass(currentVersion);
        }

        // 서버가 알지 못하는 미래 버전을 기준으로 한 요청은 안전하게 병합할 수 없다.
        if (baseVersion > currentVersion) {
            return ConflictResult.conflict(
                    baseVersion,
                    currentVersion,
                    incomingFields,
                    currentValues,
                    incomingValues
            );
        }

        Set<String> changedByOthers = loadChangedFields(
                teamId, graphId, nodeId, baseVersion, currentVersion, incomingFields
        );

        // ── 교집합 판정 ───────────────────────────────────────────────────────
        Set<String> conflicting = new HashSet<>(incomingFields);
        conflicting.retainAll(changedByOthers);

        if (conflicting.isEmpty()) {
            log.info(
                    "node conflict validate result=auto_merge teamId={} graphId={} nodeId={} baseVersion={} currentVersion={} incomingFields={} serverChangedFields={}",
                    teamId, graphId, nodeId, baseVersion, currentVersion, incomingFields, changedByOthers
            );
            // 다른 필드만 변경됨 → AUTO_MERGE (그냥 저장)
            return ConflictResult.autoMerge(baseVersion, currentVersion);
        }

        // 충돌 → diff 반환
        Map<String, Object> conflictingIncomingValues = retainValues(incomingValues, conflicting);

        log.warn(
                "node conflict validate result=conflict teamId={} graphId={} nodeId={} baseVersion={} currentVersion={} conflictFields={}",
                teamId, graphId, nodeId, baseVersion, currentVersion, conflicting
        );
        return ConflictResult.conflict(baseVersion, currentVersion,
                new ArrayList<>(conflicting), currentValues, conflictingIncomingValues);
    }

    private Map<String, Object> retainValues(Map<String, Object> values, Set<String> fields) {
        Map<String, Object> retained = new LinkedHashMap<>();
        fields.forEach(field -> retained.put(field, values.get(field)));
        return retained;
    }

    private Set<String> loadChangedFields(
            Long teamId,
            Long graphId,
            Long nodeId,
            int baseVersion,
            int currentVersion,
            List<String> incomingFields
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<Integer, List<String>> hints = editSessionService.getVersionHints(
                teamId, graphId, nodeId, baseVersion, currentVersion
        );

        Set<String> changedByOthers;
        String validatePath;
        if (hints != null) {
            validatePath = "redis-hint";
            changedByOthers = hints.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } else {
            validatePath = "db-fallback";
            List<NodeHistory> histories = nodeHistoryRepository
                    .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(nodeId, baseVersion);
            changedByOthers = histories.stream()
                    .flatMap(history -> parseFields(history).stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        sample.stop(Timer.builder("node.conflict.validate")
                .tag("path", validatePath)
                .register(meterRegistry));
        log.info(
                "node conflict validate path={} teamId={} graphId={} nodeId={} baseVersion={} currentVersion={} incomingFields={} serverChangedFields={}",
                validatePath, teamId, graphId, nodeId, baseVersion, currentVersion,
                incomingFields, changedByOthers
        );
        return changedByOthers;
    }

    private Map<String, Object> mergeValues(
            Map<String, Object> currentValues,
            Map<String, Object> incomingValues
    ) {
        Map<String, Object> proposed = new LinkedHashMap<>(currentValues);
        proposed.putAll(incomingValues);
        return proposed;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /** 현재 노드에서 충돌 필드의 값 추출 */
    private Map<String, Object> extractCurrentValues(Node node, Collection<String> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            switch (field) {
                case "subject"    -> values.put(field, node.getSubject());
                case "content"    -> values.put(field, node.getContent());
                case "symb"       -> values.put(field, node.getSymb());
                case "recordDate" -> values.put(field, node.getRecordDate());
                case "noteIds"    -> values.put(field, node.getNoteLinks().stream()
                        .map(l -> l.getNote().getId()).toList());
            }
        }
        return values;
    }

    /** NodeHistory.changedFields를 검증된 필드 집합으로 변환한다. */
    private Set<String> parseFields(NodeHistory history) {
        String json = history.getChangedFields();
        if (json == null || json.isBlank()) {
            throw corrupted(history, NodeHistoryCorruptionReason.MISSING_CHANGED_FIELDS);
        }

        List<String> fields;
        try {
            fields = objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw corrupted(
                    history,
                    NodeHistoryCorruptionReason.MALFORMED_CHANGED_FIELDS,
                    e
            );
        }

        if (fields == null) {
            throw corrupted(history, NodeHistoryCorruptionReason.MISSING_CHANGED_FIELDS);
        }
        if (fields.isEmpty()) {
            throw corrupted(history, NodeHistoryCorruptionReason.EMPTY_CHANGED_FIELDS);
        }

        Set<String> uniqueFields = new LinkedHashSet<>();
        for (String field : fields) {
            if (field == null || NodeHistoryField.fromExternalName(field).isEmpty()) {
                throw corrupted(history, NodeHistoryCorruptionReason.UNSUPPORTED_CHANGED_FIELD);
            }
            if (!uniqueFields.add(field)) {
                throw corrupted(history, NodeHistoryCorruptionReason.DUPLICATE_CHANGED_FIELD);
            }
        }
        return Collections.unmodifiableSet(uniqueFields);
    }

    private NodeHistoryCorruptedException corrupted(
            NodeHistory history,
            NodeHistoryCorruptionReason reason
    ) {
        return corrupted(history, reason, null);
    }

    private NodeHistoryCorruptedException corrupted(
            NodeHistory history,
            NodeHistoryCorruptionReason reason,
            Throwable cause
    ) {
        log.error(
                "Node 변경 이력이 손상되어 충돌 검증을 중단합니다. historyId={}, nodeId={}, version={}, reason={}, reasonMessage={}",
                history.getId(),
                history.getNodeId(),
                history.getVersion(),
                reason,
                reason.message()
        );
        if (cause == null) {
            return new NodeHistoryCorruptedException(history, reason);
        }
        return new NodeHistoryCorruptedException(history, reason, cause);
    }
}
