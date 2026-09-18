package com.example.trader.canvas;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeContentChange;
import com.example.trader.entity.NodeContentField;
import com.example.trader.entity.NodeHistory;
import com.example.trader.exception.NodeHistoryCorruptedException;
import com.example.trader.exception.NodeHistoryCorruptionReason;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.service.NodeConflictValidator;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    // 실제 ObjectMapper 사용 (JSON 파싱 로직 검증 포함)
    final ObjectMapper objectMapper = new ObjectMapper();

    NodeConflictValidator validator;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 2L;
    static final Long NODE_ID  = 3L;

    @BeforeEach
    void setUp() {
        validator = new NodeConflictValidator(editSessionService, nodeHistoryRepository, objectMapper,meterRegistry);
    }

    // ── 1. PASS 케이스 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("baseVersion == currentVersion → PASS, Redis/DB 조회 없음")
    void pass_when_no_version_change() {
        when(node.getVersion()).thenReturn(3);
        NodeContentChange change = subjectChange("새 제목");

        ConflictResult result = validator.validateContent(TEAM_ID, GRAPH_ID, NODE_ID, node, 3, change, change);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.PASS);
        assertThat(result.currentVersion()).isEqualTo(3);
        verifyNoInteractions(editSessionService, nodeHistoryRepository);
    }

    // ── 2. AUTO_MERGE 케이스 ─────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 완전 + 콘텐츠 변경이 서로 다름 → MERGE_REVIEW_REQUIRED, DB 미조회")
    void auto_merge_when_redis_complete_no_intersection() {
        // 현재 버전 4, A는 base=2 에서 편집 시작
        when(node.getVersion()).thenReturn(4);
        // v3: B가 content 변경 — A는 subject 편집 → 교집합 없음
        Map<Integer, List<String>> hints = Map.of(3, List.of("content"));
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4))
                .thenReturn(hints);

        NodeContentChange change = subjectChange("A의 새 제목");

        ConflictResult result = validator.validateContent(TEAM_ID, GRAPH_ID, NODE_ID, node, 2, change, change);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.MERGE_REVIEW_REQUIRED);
        assertThat(result.baseVersion()).isEqualTo(2);
        assertThat(result.currentVersion()).isEqualTo(4);
        assertThat(result.serverChangedFields()).containsExactly("content");
        assertThat(result.requestedFields()).containsExactly("subject");
        assertThat(result.effectiveFields()).containsExactly("subject");
        assertThat(result.proposedValues()).containsEntry("subject", "A의 새 제목");
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

        NodeContentChange change = subjectChange("A가 쓰려던 제목");

        ConflictResult result = validator.validateContent(TEAM_ID, GRAPH_ID, NODE_ID, node, 2, change, change);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).containsExactly("subject");
        // diff: 현재 DB 값 vs A가 보내려던 값
        assertThat(result.currentValues().get("subject")).isEqualTo("B가 바꾼 제목");
        assertThat(result.incomingValues().get("subject")).isEqualTo("A가 쓰려던 제목");
        // DB fallback 없어야 함
        verify(nodeHistoryRepository, never())
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(any(), any());
    }

    @Test
    @DisplayName("서버 버전 차이가 위치 변경뿐이면 콘텐츠 수정은 내부 AUTO_MERGE")
    void content_auto_merge_when_server_changed_only_position() {
        when(node.getVersion()).thenReturn(4);
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 4))
                .thenReturn(Map.of(4, List.of("x", "y")));
        NodeContentChange change = subjectChange("새 제목");

        ConflictResult result = validator.validateContent(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 3, change, change
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.AUTO_MERGE);
        verifyNoInteractions(nodeHistoryRepository);
    }

    @Test
    @DisplayName("사용자가 요청한 필드와 현재 상태에서 실제로 달라지는 필드를 구분")
    void distinguish_requested_fields_from_effective_fields() {
        when(node.getVersion()).thenReturn(4);
        when(node.getContent()).thenReturn("서버 최신 내용");
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 4))
                .thenReturn(Map.of(4, List.of("content")));
        NodeContentChange requested = NodeContentChange.of(Map.of(
                NodeContentField.SUBJECT, "새 제목",
                NodeContentField.CONTENT, "서버 최신 내용"
        ));
        NodeContentChange effective = subjectChange("새 제목");

        ConflictResult result = validator.validateContent(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 3, requested, effective
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.MERGE_REVIEW_REQUIRED);
        assertThat(result.requestedFields()).containsExactly("subject", "content");
        assertThat(result.effectiveFields()).containsExactly("subject");
        assertThat(result.conflictingFields()).isEmpty();
    }

    @Test
    @DisplayName("요청값이 최신값과 같아도 다른 콘텐츠 변경을 보지 못했다면 검토가 필요")
    void stale_no_op_still_requires_context_review() {
        when(node.getVersion()).thenReturn(4);
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 4))
                .thenReturn(Map.of(4, List.of("content")));
        NodeContentChange requested = subjectChange("이미 반영된 제목");

        ConflictResult result = validator.validateContent(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 3, requested, NodeContentChange.empty()
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.MERGE_REVIEW_REQUIRED);
        assertThat(result.requestedFields()).containsExactly("subject");
        assertThat(result.effectiveFields()).isEmpty();
        assertThat(result.serverChangedFields()).containsExactly("content");
    }

    @Test
    @DisplayName("저장 시점 CAS 충돌은 한 스냅샷의 전체 현재 값과 요청값을 반환")
    void concurrent_update_contains_complete_review_snapshot() {
        when(node.getVersion()).thenReturn(5);
        when(node.getSubject()).thenReturn("서버 최신 제목");
        when(node.getContent()).thenReturn("서버 최신 내용");
        when(node.getSymb()).thenReturn(null);
        when(node.getRecordDate()).thenReturn(java.time.LocalDate.of(2026, 9, 12));

        ConflictResult result = validator.concurrentContentUpdate(
                node,
                4,
                subjectChange("내가 저장할 제목")
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONCURRENT_UPDATE);
        assertThat(result.baseVersion()).isEqualTo(4);
        assertThat(result.currentVersion()).isEqualTo(5);
        assertThat(result.conflictingFields()).containsExactly("subject");
        assertThat(result.currentValues())
                .containsEntry("subject", "서버 최신 제목")
                .containsEntry("content", "서버 최신 내용")
                .containsEntry("symb", null)
                .containsEntry("recordDate", java.time.LocalDate.of(2026, 9, 12))
                .containsEntry("noteIds", List.of());
        assertThat(result.incomingValues()).containsEntry("subject", "내가 저장할 제목");
    }

    // ── 4. DB fallback 케이스 ────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 불완전 → DB fallback + 콘텐츠 변경이 서로 다름 → MERGE_REVIEW_REQUIRED")
    void auto_merge_when_redis_incomplete_db_fallback_no_intersection() {
        when(node.getVersion()).thenReturn(4);
        // Redis 힌트 불완전 → null 반환
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4))
                .thenReturn(null);
        // DB: v3에서 content 변경됨, A는 subject만 편집 → 교집합 없음
        when(nodeHistoryRepository.findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 2))
                .thenReturn(List.of(history(3, "[\"content\"]")));

        NodeContentChange change = subjectChange("A의 새 제목");

        ConflictResult result = validator.validateContent(TEAM_ID, GRAPH_ID, NODE_ID, node, 2, change, change);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.MERGE_REVIEW_REQUIRED);
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

        NodeContentChange change = subjectChange("A의 제목");

        ConflictResult result = validator.validateContent(TEAM_ID, GRAPH_ID, NODE_ID, node, 2, change, change);

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

        NodeContentChange change = subjectChange("A의 제목");

        ConflictResult result = validator.validateContent(TEAM_ID, GRAPH_ID, NODE_ID, node, 2, change, change);

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).containsExactly("subject");
    }

    @Test
    @DisplayName("DB 변경 이력이 잘못된 JSON이면 자동 병합하지 않고 검증을 중단")
    void fail_closed_when_history_json_is_malformed() {
        assertDbHistoryCorrupted("not-json", NodeHistoryCorruptionReason.MALFORMED_CHANGED_FIELDS);
    }

    @Test
    @DisplayName("DB 변경 이력이 빈 배열이면 History 불변식 위반으로 검증을 중단")
    void fail_closed_when_history_fields_are_empty() {
        assertDbHistoryCorrupted("[]", NodeHistoryCorruptionReason.EMPTY_CHANGED_FIELDS);
    }

    @Test
    @DisplayName("DB 변경 이력에 지원하지 않는 필드가 있으면 검증을 중단")
    void fail_closed_when_history_contains_unsupported_field() {
        assertDbHistoryCorrupted(
                "[\"unknown\"]",
                NodeHistoryCorruptionReason.UNSUPPORTED_CHANGED_FIELD
        );
    }

    @Test
    @DisplayName("DB 변경 이력에 중복 필드가 있으면 검증을 중단")
    void fail_closed_when_history_contains_duplicate_field() {
        assertDbHistoryCorrupted(
                "[\"subject\",\"subject\"]",
                NodeHistoryCorruptionReason.DUPLICATE_CHANGED_FIELD
        );
    }

    @Test
    @DisplayName("DB의 x,y 변경 이력은 유효한 위치 이력으로 처리")
    void accept_position_fields_from_db_history() {
        when(node.getVersion()).thenReturn(4);
        when(node.getX()).thenReturn(10.0);
        when(node.getY()).thenReturn(20.0);
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 4))
                .thenReturn(null);
        when(nodeHistoryRepository.findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 3))
                .thenReturn(List.of(history(4, "[\"x\",\"y\"]")));

        ConflictResult result = validator.validatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 3, 30.0, 40.0
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    @DisplayName("위치 변경과 서버의 content 변경은 겹치지 않아 AUTO_MERGE")
    void position_auto_merge_when_changed_fields_do_not_overlap() {
        when(node.getVersion()).thenReturn(4);
        when(node.getX()).thenReturn(10.0);
        when(node.getY()).thenReturn(20.0);
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4))
                .thenReturn(Map.of(3, List.of("content"), 4, List.of("subject")));

        ConflictResult result = validator.validatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 2, 30.0, 40.0
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.AUTO_MERGE);
        assertThat(result.currentVersion()).isEqualTo(4);
        verifyNoInteractions(nodeHistoryRepository);
    }

    @Test
    @DisplayName("위치 변경 이후 stale 위치 요청은 x,y 충돌과 양쪽 값을 반환")
    void position_conflict_when_position_fields_overlap() {
        when(node.getVersion()).thenReturn(4);
        when(node.getX()).thenReturn(10.0);
        when(node.getY()).thenReturn(20.0);
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 4))
                .thenReturn(Map.of(4, List.of("x", "y")));

        ConflictResult result = validator.validatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 3, 30.0, 40.0
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.conflictingFields()).containsExactlyInAnyOrder("x", "y");
        assertThat(result.currentValues()).containsEntry("x", 10.0).containsEntry("y", 20.0);
        assertThat(result.incomingValues()).containsEntry("x", 30.0).containsEntry("y", 40.0);
    }

    @Test
    @DisplayName("서버보다 미래인 baseVersion은 조회 없이 충돌 처리")
    void position_conflict_when_base_version_is_in_the_future() {
        when(node.getVersion()).thenReturn(4);
        when(node.getX()).thenReturn(10.0);
        when(node.getY()).thenReturn(20.0);

        ConflictResult result = validator.validatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 5, 30.0, 40.0
        );

        assertThat(result.type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(result.baseVersion()).isEqualTo(5);
        assertThat(result.currentVersion()).isEqualTo(4);
        verifyNoInteractions(editSessionService, nodeHistoryRepository);
    }

    @Test
    @DisplayName("검증된 변경 객체가 실제 적용 필드만 노출")
    void content_change_exposes_exact_fields() {
        NodeContentChange change = subjectChange("새 제목");

        assertThat(change.fieldNames()).containsExactly("subject");
        assertThat(change.externalValues()).containsOnly(Map.entry("subject", "새 제목"));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private NodeContentChange subjectChange(String subject) {
        return NodeContentChange.of(Map.of(NodeContentField.SUBJECT, subject));
    }

    private void assertDbHistoryCorrupted(
            String changedFields,
            NodeHistoryCorruptionReason expectedReason
    ) {
        when(node.getVersion()).thenReturn(4);
        when(editSessionService.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 4))
                .thenReturn(null);
        when(nodeHistoryRepository.findByNodeIdAndVersionGreaterThanOrderByVersionAsc(NODE_ID, 3))
                .thenReturn(List.of(history(4, changedFields)));

        assertThatThrownBy(() -> validator.validateContent(
                TEAM_ID,
                GRAPH_ID,
                NODE_ID,
                node,
                3,
                subjectChange("새 제목"),
                subjectChange("새 제목")
        )).isInstanceOfSatisfying(
                NodeHistoryCorruptedException.class,
                exception -> {
                    assertThat(exception.getReason()).isEqualTo(expectedReason);
                    assertThat(exception.getHistoryId()).isEqualTo(100L);
                    assertThat(exception.getNodeId()).isEqualTo(NODE_ID);
                    assertThat(exception.getVersion()).isEqualTo(4);
                    assertThat(exception.getMessage())
                            .isEqualTo("변경 이력을 해석할 수 없어 충돌을 검증하지 못했습니다.");
                }
        );
    }

    private NodeHistory history(int version, String changedFieldsJson) {
        return NodeHistory.builder()
                .id(100L)
                .nodeId(NODE_ID).teamId(TEAM_ID).graphId(GRAPH_ID)
                .version(version).changedBy(99L)
                .changedFields(changedFieldsJson)
                .changedAt(LocalDateTime.now())
                .build();
    }
}
