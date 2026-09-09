package com.example.trader.service;

import com.example.trader.dto.UpdateNodePositionRes;
import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.entity.Node;
import com.example.trader.exception.BaseException;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.repository.NodeRepository;
import com.example.trader.ws.raw.lock.CanvasLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamNodePositionServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long GRAPH_ID = 2L;
    private static final Long NODE_ID = 3L;
    private static final Long USER_ID = 4L;

    @Mock NodeRepository nodeRepository;
    @Mock NodeConflictValidator conflictValidator;
    @Mock CanvasLockService canvasLockService;
    @Mock TeamNodePositionWriter writer;
    @Mock NodeCacheService nodeCacheService;
    @Mock GraphCacheService graphCacheService;
    @Mock Node node;

    TeamNodePositionService service;

    @BeforeEach
    void setUp() {
        service = new TeamNodePositionService(
                nodeRepository,
                conflictValidator,
                canvasLockService,
                writer,
                nodeCacheService,
                graphCacheService
        );
    }

    @Test
    @DisplayName("이동 락 소유자가 아니면 DB를 조회하지 않고 거부")
    void reject_when_user_does_not_hold_lock() {
        when(canvasLockService.isHeldBy(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 3, 10.0, 20.0
        )).isInstanceOf(BaseException.class)
          .hasMessageContaining("이동 락");

        verifyNoInteractions(nodeRepository, conflictValidator, writer);
    }

    @Test
    @DisplayName("다른 필드 변경은 병합하고 검증한 현재 버전으로 CAS 저장")
    void auto_merge_uses_validated_current_version_as_cas_version() {
        when(canvasLockService.isHeldBy(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID)).thenReturn(true);
        when(nodeRepository.findTeamNode(NODE_ID, GRAPH_ID, TEAM_ID)).thenReturn(Optional.of(node));
        when(conflictValidator.validatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, node, 3, 10.0, 20.0
        )).thenReturn(ConflictResult.autoMerge(3, 4));

        when(writer.write(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 4, 10.0, 20.0))
                .thenReturn(Optional.of(5));

        UpdateNodePositionRes response = service.updatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 3, 10.0, 20.0
        );
        assertThat(response.version()).isEqualTo(5);
        assertThat(response.autoMerged()).isTrue();

        verify(writer).write(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 4, 10.0, 20.0);
        verify(nodeCacheService).evictPageNodes(GRAPH_ID);
        verify(graphCacheService).evictGraph(GRAPH_ID);
    }

    @Test
    @DisplayName("검증 후 CAS 경합이 발생하면 최신 상태를 다시 검증하고 실제 위치 충돌을 반환")
    void revalidate_after_cas_race_and_return_position_conflict() {
        Node latest = mock(Node.class);
        when(canvasLockService.isHeldBy(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID)).thenReturn(true);
        when(nodeRepository.findTeamNode(NODE_ID, GRAPH_ID, TEAM_ID))
                .thenReturn(Optional.of(node), Optional.of(latest));

        ConflictResult first = ConflictResult.autoMerge(3, 4);
        ConflictResult conflict = ConflictResult.conflict(
                3,
                5,
                List.of("x", "y"),
                Map.of("x", 11.0, "y", 21.0),
                Map.of("x", 10.0, "y", 20.0)
        );
        when(conflictValidator.validatePosition(TEAM_ID, GRAPH_ID, NODE_ID, node, 3, 10.0, 20.0))
                .thenReturn(first);
        when(conflictValidator.validatePosition(TEAM_ID, GRAPH_ID, NODE_ID, latest, 3, 10.0, 20.0))
                .thenReturn(conflict);
        when(writer.write(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 4, 10.0, 20.0))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePosition(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 3, 10.0, 20.0
        )).isInstanceOf(NodeConflictException.class);

        verify(writer, times(1)).write(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 4, 10.0, 20.0);
        verifyNoInteractions(nodeCacheService, graphCacheService);
    }
}
