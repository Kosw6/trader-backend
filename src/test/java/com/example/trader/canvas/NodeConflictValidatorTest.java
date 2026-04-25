package com.example.trader.canvas;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeHistory;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.service.NodeConflictValidator;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * NodeConflictValidator 단위 테스트.
 *
 * 검증 목표:
 *  - 6가지 분기가 올바르게 타지는지
 *  - Redis / DB 조회가 불필요한 경우 실제로 호출되지 않는지 (verify never)
 *  - CONFLICT 시 diff 값이 정확히 반환되는지
 */
@ExtendWith(MockitoExtension.class)
class NodeConflictValidatorTest {

    @Mock NodeEditSessionService editSessionService;
    @Mock NodeHistoryRepository  nodeHistoryRepository;
    @Mock Node                   node;

    // 실제 ObjectMapper 사용 (JSON 파싱 로직 검증 포함)
    final ObjectMapper objectMapper = new ObjectMapper();

    NodeConflictValidator validator;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 2L;
    static final Long NODE_ID  = 3L;

    @BeforeEach
    void setUp() {
        validator = new NodeConflictValidator(editSessionService, nodeHistoryRepository, objectMapper);
    }

    // ── 1. PASS 케이스 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("baseVersion 없음 → PASS, Redis/DB 조회 없음")
    void pass_when_baseVersion_null() {
        when(node.getVersion()).thenReturn(5);
        RequestNodeDto req = req(null, "새 제목");

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.PASS);
        // Redis, DB 조회 없어야 함
        verifyNoInteractions(editSessionService, nodeHistoryRepository);
    }

    @Test
    @DisplayName("baseVersion >= currentVersion → PASS, Redis/DB 조회 없음")
    void pass_when_no_version_change() {
        when(node.getVersion()).thenReturn(3);
        RequestNodeDto req = req(3, "새 제목");

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.PASS);
        assertThat(result.currentVersion()).isEqualTo(3);
        verifyNoInteractions(editSessionService, nodeHistoryRepository);
    }

    // ── 2. AUTO_MERGE 케이스 ─────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 완전 + 교집합 없음 → AUTO_MERGE, DB 미조회")
    void auto_merge_when_redis_complete_no_intersection() {
        // 현재 버전 4, A는 base=2 에서 편집 시작
        when(node.getVersion()).thenReturn(4);
        // v3: B가 content 변경 — A는 subject 편집 → 교집합 없음
        Map<Integer, List<String>> hints = Map.of(3, List.of("content"));
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4))
                .thenReturn(hints);

        RequestNodeDto req = req(2, "A의 새 제목"); // subject 만 변경

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.AUTO_MERGE);
        assertThat(result.baseVersion()).isEqualTo(2);
        assertThat(result.currentVersion()).isEqualTo(4);
        // DB fallback 없어야 함
        verify(nodeHistoryRepository, never())
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(any(), any());
    }

    // ── 3. CONFLICT 케이스 (Redis 완전) ──────────────────────────────────────

    @Test
    @DisplayName("Redis 완전 + 교집합 있음 → CONFLICT, diff 값 포함, DB 미조회")
    void conflict_when_redis_complete_with_intersection() {
        when(node.getVersion()).thenReturn(3);
        when(node.getSubject()).thenReturn("B가 바꾼 제목");
        // v3: B가 subject 변경, A도 subject 편집 → 교집합 = {subject}
        Map<Integer, List<String>> hints = Map.of(3, List.of("subject"));
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 3))
                .thenReturn(hints);

        RequestNodeDto req = req(2, "A가 쓰려던 제목");

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).containsExactly("subject");
        // diff: 현재 DB 값 vs A가 보내려던 값
        assertThat(result.currentValues().get("subject")).isEqualTo("B가 바꾼 제목");
        assertThat(result.incomingValues().get("subject")).isEqualTo("A가 쓰려던 제목");
        // DB fallback 없어야 함
        verify(nodeHistoryRepository, never())
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(any(), any());
    }

    // ── 4. DB fallback 케이스 ────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 불완전 → DB fallback + 교집합 없음 → AUTO_MERGE")
    void auto_merge_when_redis_incomplete_db_fallback_no_intersection() {
        when(node.getVersion()).thenReturn(4);
        // Redis 힌트 불완전 → null 반환
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4))
                .thenReturn(null);
        // DB: v3에서 content 변경됨, A는 subject만 편집 → 교집합 없음
        when(nodeHistoryRepository.findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 2))
                .thenReturn(List.of(history(3, "[\"content\"]")));

        RequestNodeDto req = req(2, "A의 새 제목"); // subject만

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.AUTO_MERGE);
        // DB 조회 반드시 발생해야 함
        verify(nodeHistoryRepository)
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 2);
    }

    @Test
    @DisplayName("Redis 불완전 → DB fallback + 교집합 있음 → CONFLICT")
    void conflict_when_redis_incomplete_db_fallback_with_intersection() {
        when(node.getVersion()).thenReturn(4);
        when(node.getSubject()).thenReturn("DB 현재 제목");
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4))
                .thenReturn(null);
        // DB: v3에서 subject 변경됨, A도 subject 편집 → 충돌
        when(nodeHistoryRepository.findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 2))
                .thenReturn(List.of(history(3, "[\"subject\"]")));

        RequestNodeDto req = req(2, "A의 제목");

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).contains("subject");
        assertThat(result.currentValues().get("subject")).isEqualTo("DB 현재 제목");
        assertThat(result.incomingValues().get("subject")).isEqualTo("A의 제목");
        verify(nodeHistoryRepository)
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 2);
    }

    @Test
    @DisplayName("Redis 불완전 + DB 이력에 여러 버전 → 모든 변경 필드 집계")
    void conflict_when_multiple_db_histories() {
        when(node.getVersion()).thenReturn(5);
        when(node.getSubject()).thenReturn("최종 DB 제목");
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 5))
                .thenReturn(null);
        // v3: content, v4: subject 변경 → 합집합 = {content, subject}
        when(nodeHistoryRepository.findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 2))
                .thenReturn(List.of(
                        history(3, "[\"content\"]"),
                        history(4, "[\"subject\"]")
                ));

        RequestNodeDto req = req(2, "A의 제목"); // subject 수정

        ConflictResult result = validator.validate(TEAM_ID, GRAPH_ID, NODE_ID, node, req);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).containsExactly("subject");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private RequestNodeDto req(Integer baseVersion, String subject) {
        RequestNodeDto dto = new RequestNodeDto();
        dto.setSubject(subject);
        dto.setBaseVersion(baseVersion);
        return dto;
    }

    private NodeHistory history(int version, String changedFieldsJson) {
        return NodeHistory.builder()
                .nodeId(NODE_ID).teamId(TEAM_ID).graphId(GRAPH_ID)
                .version(version).changedBy(99L)
                .changedFields(changedFieldsJson)
                .changedAt(LocalDateTime.now())
                .build();
    }
}
