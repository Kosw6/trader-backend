package com.example.trader.canvas;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.entity.NodeHistory;
import com.example.trader.entity.Page;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.repository.*;
import com.example.trader.service.*;
import com.example.trader.support.fixtures.TestFixtures;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.example.trader.entity.Node;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NodeService.updateTeamNode() 단위 테스트.
 *
 * 검증 목표:
 *  - PASS/AUTO_MERGE 시 저장 흐름 전체(incrementVersion, history, hint, endSession)
 *  - CONFLICT + force=false → 예외 throw, 저장 없음
 *  - CONFLICT + force=true  → validate skip, 저장 진행
 *  - 변경 필드 없을 때 history/hint 미저장
 */
@ExtendWith(MockitoExtension.class)
class NodeServiceCanvasTest {

    // ── 의존성 모킹 ──────────────────────────────────────────────────────────
    @Mock NodeRepository         nodeRepository;
    @Mock PageRepository         pageRepository;
    @Mock NoteRepository         noteRepository;
    @Mock EdgeRepository         edgeRepository;
    @Mock EntityManager          em;
    @Mock NodeCacheService       nodeCacheService;
    @Mock GraphCacheService      graphCacheService;
    @Mock NodeHistoryRepository  nodeHistoryRepository;
    @Mock NodeConflictValidator  conflictValidator;
    @Mock NodeEditSessionService editSessionService;

    // 실제 ObjectMapper — JSON 직렬화 검증용
    final ObjectMapper objectMapper = new ObjectMapper();

    NodeService nodeService;

    // 테스트 픽스처
    Node   node;
    Page   mockPage;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 2L;
    static final Long NODE_ID  = 3L;
    static final Long USER_ID  = 10L;

    @BeforeEach
    void setUp() {
        // 수동 생성: @RequiredArgsConstructor 순서 맞춰서
        nodeService = new NodeService(
                nodeRepository, pageRepository, noteRepository, edgeRepository,
                em, objectMapper,
                nodeCacheService, graphCacheService,
                nodeHistoryRepository, conflictValidator, editSessionService
        );

        mockPage = mock(Page.class);
        node = TestFixtures.node(mockPage);
        // 초기 버전 2로 설정
        ReflectionTestUtils.setField(node, "version", 2);

        // 공통 스텁: 팀 노드 존재 여부, 노드 조회
        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(NODE_ID, GRAPH_ID, TEAM_ID))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(NODE_ID))
                .thenReturn(Optional.of(node));
    }

    // ── PASS → 정상 저장 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PASS → 버전 증가, history/hint 저장, 편집 세션 종료")
    void update_success_on_pass() {
        when(conflictValidator.validate(eq(TEAM_ID), eq(GRAPH_ID), eq(NODE_ID), eq(node), any()))
                .thenReturn(ConflictResult.pass(2));
        when(conflictValidator.extractChangedFields(any()))
                .thenReturn(List.of("subject"));
        when(nodeHistoryRepository.save(any())).thenReturn(mock(NodeHistory.class));

        RequestNodeDto req = req(2, "새 제목", false);

        nodeService.updateTeamNode(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, req);

        // 버전 증가 확인
        assertThat(node.getVersion()).isEqualTo(3);

        // history DB 저장 확인 — changedFields에 "subject" 포함 검증
        ArgumentCaptor<NodeHistory> historyCaptor = ArgumentCaptor.forClass(NodeHistory.class);
        verify(nodeHistoryRepository).save(historyCaptor.capture());
        NodeHistory saved = historyCaptor.getValue();
        assertThat(saved.getVersion()).isEqualTo(3);
        assertThat(saved.getChangedBy()).isEqualTo(USER_ID);
        assertThat(saved.getChangedFields()).contains("subject");

        // Redis 힌트 저장 확인
        verify(editSessionService).saveVersionHint(TEAM_ID, GRAPH_ID, NODE_ID, 3, List.of("subject"), USER_ID);

        // 편집 세션 종료 확인
        verify(editSessionService).endEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        // 캐시 무효화 확인
        verify(nodeCacheService).evictPageNodes(GRAPH_ID);
        verify(graphCacheService).evictGraph(GRAPH_ID);
    }

    // ── AUTO_MERGE → 정상 저장 ───────────────────────────────────────────────

    @Test
    @DisplayName("AUTO_MERGE → PASS와 동일하게 저장 진행")
    void update_success_on_auto_merge() {
        when(conflictValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(ConflictResult.autoMerge(2, 4));
        when(conflictValidator.extractChangedFields(any()))
                .thenReturn(List.of("content"));
        when(nodeHistoryRepository.save(any())).thenReturn(mock(NodeHistory.class));

        nodeService.updateTeamNode(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, req(2, null, false));

        assertThat(node.getVersion()).isEqualTo(3);
        verify(nodeHistoryRepository).save(any());
        verify(editSessionService).saveVersionHint(any(), any(), any(), eq(3), any(), any());
        verify(editSessionService).endEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);
    }

    // ── CONFLICT + force=false → 예외, 저장 없음 ────────────────────────────

    @Test
    @DisplayName("CONFLICT + force=false → NodeConflictException throw, 저장/힌트/세션 종료 없음")
    void throws_conflict_exception_when_conflict_and_not_force() {
        ConflictResult conflictResult = ConflictResult.conflict(
                2, 3, List.of("subject"),
                java.util.Map.of("subject", "B의 제목"),
                java.util.Map.of("subject", "A의 제목")
        );
        when(conflictValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(conflictResult);

        RequestNodeDto req = req(2, "A의 제목", false);

        // 예외 발생 + ConflictResult 포함 여부
        NodeConflictException ex = assertThrows(NodeConflictException.class,
                () -> nodeService.updateTeamNode(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, req));

        assertThat(ex.getResult().type()).isEqualTo(ConflictResult.ConflictType.CONFLICT);
        assertThat(ex.getResult().conflictingFields()).containsExactly("subject");

        // 저장 관련 호출 없어야 함
        verify(nodeHistoryRepository, never()).save(any());
        verify(editSessionService, never()).saveVersionHint(any(), any(), any(), anyInt(), any(), any());
        // 충돌 시에는 편집 세션 유지 (endEditSession 호출 안 됨)
        verify(editSessionService, never()).endEditSession(any(), any(), any(), any());

        // 버전 변경 없어야 함
        assertThat(node.getVersion()).isEqualTo(2);
    }

    // ── CONFLICT + force=true → validate skip, 저장 ─────────────────────────

    @Test
    @DisplayName("CONFLICT + force=true → validate 호출 안 됨, 저장 진행")
    void skip_validate_when_force_true() {
        when(conflictValidator.extractChangedFields(any()))
                .thenReturn(List.of("subject"));
        when(nodeHistoryRepository.save(any())).thenReturn(mock(NodeHistory.class));

        RequestNodeDto req = req(2, "A의 제목", true); // force=true

        nodeService.updateTeamNode(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, req);

        // validate 호출되지 않아야 함
        verify(conflictValidator, never()).validate(any(), any(), any(), any(), any());

        // 저장은 정상 진행
        assertThat(node.getVersion()).isEqualTo(3);
        verify(nodeHistoryRepository).save(any());
        verify(editSessionService).endEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);
    }

    // ── 변경 필드 없음 → history/hint 미저장 ────────────────────────────────

    @Test
    @DisplayName("변경 필드 없음 → history DB 저장 없음, Redis 힌트 저장 없음")
    void no_history_when_no_changed_fields() {
        when(conflictValidator.validate(any(), any(), any(), any(), any()))
                .thenReturn(ConflictResult.pass(2));
        // 변경 필드 없음 (빈 리스트)
        when(conflictValidator.extractChangedFields(any()))
                .thenReturn(List.of());

        RequestNodeDto req = req(2, null, false); // 아무 필드도 안 바꿈

        nodeService.updateTeamNode(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, req);

        // history, hint 저장 없어야 함
        verify(nodeHistoryRepository, never()).save(any());
        verify(editSessionService, never()).saveVersionHint(any(), any(), any(), anyInt(), any(), any());

        // 버전은 증가함 (필드 변경 여부와 무관)
        assertThat(node.getVersion()).isEqualTo(3);
        // 세션 종료는 항상 발생
        verify(editSessionService).endEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private RequestNodeDto req(Integer baseVersion, String subject, boolean force) {
        RequestNodeDto dto = new RequestNodeDto();
        dto.setBaseVersion(baseVersion);
        dto.setSubject(subject);
        dto.setForce(force);
        return dto;
    }
}
