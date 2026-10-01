package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.exception.ConcurrentNodeUpdateException;
import com.example.trader.exception.NodeConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamNodeContentUpdateCoordinatorTest {

    @Mock NodeService nodeService;
    @Mock TeamNodeConflictSnapshotService conflictSnapshotService;

    private TeamNodeContentUpdateCoordinator coordinator;
    private UpdateTeamNodeContentRequest request;

    @BeforeEach
    void setUp() {
        coordinator = new TeamNodeContentUpdateCoordinator(nodeService, conflictSnapshotService);
        request = new UpdateTeamNodeContentRequest();
        request.setSubject("내 수정");
        request.setDirtyFields(List.of("subject"));
        request.setBaseVersion(3);
    }

    @Test
    @DisplayName("첫 CAS 실패는 새 트랜잭션에서 한 번만 다시 검증하고 성공할 수 있음")
    void retries_once_after_first_cas_failure() {
        ResponseNodeDto expected = org.mockito.Mockito.mock(ResponseNodeDto.class);
        when(nodeService.updateTeamNode(1L, 2L, 3L, 4L, request))
                .thenThrow(new ConcurrentNodeUpdateException(new RuntimeException("동시 갱신")))
                .thenReturn(expected);

        ResponseNodeDto actual = coordinator.update(1L, 2L, 3L, 4L, request);

        assertThat(actual).isSameAs(expected);
        verify(nodeService, org.mockito.Mockito.times(2))
                .updateTeamNode(1L, 2L, 3L, 4L, request);
        verify(conflictSnapshotService, never())
                .resolveAfterRetryExhausted(1L, 2L, 3L, 4L, request);
    }

    @Test
    @DisplayName("두 번째 CAS 실패는 최신 단일 스냅샷을 담은 409로 전환")
    void returns_conflict_snapshot_after_retry_exhausted() {
        ConflictResult snapshot = ConflictResult.concurrentUpdate(
                3,
                5,
                List.of("subject"),
                Map.of("subject", "서버 최신값"),
                Map.of("subject", "내 수정")
        );
        when(nodeService.updateTeamNode(1L, 2L, 3L, 4L, request))
                .thenThrow(new ConcurrentNodeUpdateException(new RuntimeException("동시 갱신")));
        when(conflictSnapshotService.resolveAfterRetryExhausted(1L, 2L, 3L, 4L, request))
                .thenReturn(snapshot);

        assertThatThrownBy(() -> coordinator.update(1L, 2L, 3L, 4L, request))
                .isInstanceOfSatisfying(NodeConflictException.class, exception ->
                        assertThat(exception.getResult()).isSameAs(snapshot));

        verify(nodeService, org.mockito.Mockito.times(2))
                .updateTeamNode(1L, 2L, 3L, 4L, request);
    }

    @Test
    @DisplayName("재검증에서 실제 같은 필드 충돌이 확인되면 추가 스냅샷 조회 없이 그대로 반환")
    void propagates_business_conflict_from_retry() {
        NodeConflictException businessConflict = new NodeConflictException(
                ConflictResult.conflict(
                        3,
                        4,
                        List.of("subject"),
                        Map.of("subject", "서버값"),
                        Map.of("subject", "내 수정")
                )
        );
        when(nodeService.updateTeamNode(1L, 2L, 3L, 4L, request))
                .thenThrow(new ConcurrentNodeUpdateException(new RuntimeException("동시 갱신")))
                .thenThrow(businessConflict);

        assertThatThrownBy(() -> coordinator.update(1L, 2L, 3L, 4L, request))
                .isSameAs(businessConflict);

        verify(conflictSnapshotService, never())
                .resolveAfterRetryExhausted(1L, 2L, 3L, 4L, request);
    }
}
