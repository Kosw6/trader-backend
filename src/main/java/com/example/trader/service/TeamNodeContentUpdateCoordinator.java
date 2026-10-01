package com.example.trader.service;

import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.exception.ConcurrentNodeUpdateException;
import com.example.trader.exception.NodeConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamNodeContentUpdateCoordinator {

    private static final int MAX_WRITE_ATTEMPTS = 2;

    private final NodeService nodeService;
    private final TeamNodeConflictSnapshotService conflictSnapshotService;

    /**
     * 첫 CAS 실패는 최신 상태에서 한 번 재검증한다. 두 번째 실패는 사용자 확인이 필요한 409로 바꾼다.
     */
    public ResponseNodeDto update(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId,
            UpdateTeamNodeContentRequest request
    ) {
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                return nodeService.updateTeamNode(teamId, graphId, nodeId, userId, request);
            } catch (ConcurrentNodeUpdateException exception) {
                if (attempt == MAX_WRITE_ATTEMPTS) {
                    throw new NodeConflictException(
                            conflictSnapshotService.resolveAfterRetryExhausted(
                                    teamId,
                                    graphId,
                                    nodeId,
                                    userId,
                                    request
                            )
                    );
                }
                log.info(
                        "node content CAS retry teamId={} graphId={} nodeId={} userId={} baseVersion={} attempt={}",
                        teamId,
                        graphId,
                        nodeId,
                        userId,
                        request.getBaseVersion(),
                        attempt
                );
            }
        }
        throw new IllegalStateException("Node 수정 재시도 흐름이 종료되지 않았습니다.");
    }
}
