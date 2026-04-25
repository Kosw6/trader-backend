package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeHistory;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *   <li>A 편집 필드 ∩ 변경 필드 = ∅ → AUTO_MERGE</li>
 *   <li>A 편집 필드 ∩ 변경 필드 ≠ ∅ → CONFLICT (diff 포함 반환)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeConflictValidator {

    private final NodeEditSessionService editSessionService;
    private final NodeHistoryRepository  nodeHistoryRepository;
    private final ObjectMapper           objectMapper;

    /**
     * 충돌 여부를 검증하여 {@link ConflictResult}를 반환.
     *
     * @param teamId  팀 ID
     * @param graphId 그래프 ID
     * @param nodeId  노드 ID
     * @param node    현재 DB 상태 (findByIdWithLinks 로드된 것)
     * @param req     클라이언트 요청 DTO
     */
    public ConflictResult validate(Long teamId, Long graphId, Long nodeId,
                                   Node node, RequestNodeDto req) {

        Integer baseVersion = req.getBaseVersion();

        // baseVersion 미제공 → 구버전 클라이언트, 충돌 체크 skip
        if (baseVersion == null) {
            return ConflictResult.pass(node.getVersion());
        }

        int currentVersion = node.getVersion();

        // 버전 변경 없음 → 충돌 없음
        if (baseVersion >= currentVersion) {
            return ConflictResult.pass(currentVersion);
        }

        // A가 변경하려는 필드 추출
        List<String> incomingFields = extractChangedFields(req);

        // ── Redis 힌트 체인 확인 ───────────────────────────────────────────────
        Map<Integer, List<String>> hints =
                editSessionService.getVersionHints(teamId, graphId, nodeId, baseVersion, currentVersion);

        Set<String> changedByOthers;

        if (hints != null) {
            // ✅ 힌트 완전 → Redis 집계
            changedByOthers = hints.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
        } else {
            // ❌ 힌트 불완전 → DB NodeHistory fallback
            log.debug("[Conflict] Hint chain incomplete → DB fallback. nodeId={} base={} current={}",
                    nodeId, baseVersion, currentVersion);

            List<NodeHistory> histories = nodeHistoryRepository
                    .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(nodeId, baseVersion);

            changedByOthers = histories.stream()
                    .flatMap(h -> parseFields(h.getChangedFields()).stream())
                    .collect(Collectors.toSet());
        }

        // ── 교집합 판정 ───────────────────────────────────────────────────────
        Set<String> conflicting = new HashSet<>(incomingFields);
        conflicting.retainAll(changedByOthers);

        if (conflicting.isEmpty()) {
            // 다른 필드만 변경됨 → AUTO_MERGE (그냥 저장)
            return ConflictResult.autoMerge(baseVersion, currentVersion);
        }

        // 충돌 → diff 반환
        Map<String, Object> currentValues  = extractCurrentValues(node, conflicting);
        Map<String, Object> incomingValues = extractIncomingValues(req, conflicting);

        return ConflictResult.conflict(baseVersion, currentVersion,
                new ArrayList<>(conflicting), currentValues, incomingValues);
    }

    /**
     * RequestNodeDto에서 null이 아닌(=변경 의도가 있는) 필드명 추출.
     * NodeService에서 changedFields 계산 시에도 재사용.
     */
    public List<String> extractChangedFields(RequestNodeDto req) {
        List<String> fields = new ArrayList<>();
        if (req.getSubject()    != null) fields.add("subject");
        if (req.getContent()    != null) fields.add("content");
        if (req.getSymb()       != null) fields.add("symb");
        if (req.getRecordDate() != null) fields.add("recordDate");
        if (!req.isNoteIdsOmitted())     fields.add("noteIds");
        return fields;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /** 현재 노드에서 충돌 필드의 값 추출 */
    private Map<String, Object> extractCurrentValues(Node node, Set<String> fields) {
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

    /** 요청 DTO에서 충돌 필드의 값 추출 */
    private Map<String, Object> extractIncomingValues(RequestNodeDto req, Set<String> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            switch (field) {
                case "subject"    -> values.put(field, req.getSubject());
                case "content"    -> values.put(field, req.getContent());
                case "symb"       -> values.put(field, req.getSymb());
                case "recordDate" -> values.put(field, req.getRecordDate());
                case "noteIds"    -> values.put(field, req.getNoteIds());
            }
        }
        return values;
    }

    /** NodeHistory.changedFields JSON 문자열 → List<String> */
    private List<String> parseFields(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[Conflict] changedFields parse failed: {}", json);
            return List.of();
        }
    }
}
