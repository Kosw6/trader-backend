package com.example.trader.service;

import com.example.trader.dto.UpdateNodePositionRes;
import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.entity.Node;
import com.example.trader.exception.BaseException;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.NodeRepository;
import com.example.trader.ws.raw.lock.CanvasLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 팀 노드 드래그 완료 유스케이스.
 *
 * <p>Redis 락 확인과 충돌 판단은 트랜잭션 밖에서 수행하고, 실제 DB 변경은
 * {@link TeamNodePositionWriter}의 짧은 트랜잭션에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamNodePositionService {

    private static final int MAX_CAS_ATTEMPTS = 3;

    private final NodeRepository nodeRepository;
    private final NodeConflictValidator conflictValidator;
    private final CanvasLockService canvasLockService;
    private final TeamNodePositionWriter writer;
    private final NodeCacheService nodeCacheService;
    private final GraphCacheService graphCacheService;

    public UpdateNodePositionRes updatePosition(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId,
            Integer baseVersion,
            double x,
            double y
    ) {
        if (baseVersion == null || baseVersion < 0) {
            throw new BaseException(
                    BaseResponseStatus.INVALID_REQUEST,
                    "팀 노드 위치 변경에는 0 이상의 baseVersion이 필요합니다."
            );
        }

        requireLockOwnership(teamId, graphId, nodeId, userId);

        for (int attempt = 1; attempt <= MAX_CAS_ATTEMPTS; attempt++) {
            Node current = nodeRepository.findTeamNode(nodeId, graphId, teamId)
                    .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_NODE));

            ConflictResult conflict = conflictValidator.validatePosition(
                    teamId, graphId, nodeId, current, baseVersion, x, y
            );
            if (conflict.isConflict()) {
                throw new NodeConflictException(conflict);
            }

            // 검증 도중 락이 만료되거나 다른 사용자에게 넘어간 경우 저장하지 않는다.
            requireLockOwnership(teamId, graphId, nodeId, userId);

            var written = writer.write(
                    teamId,
                    graphId,
                    nodeId,
                    userId,
                    conflict.currentVersion(),
                    x,
                    y
            );
            if (written.isPresent()) {
                evictCachesBestEffort(graphId);
                boolean autoMerged = conflict.type() == ConflictResult.ConflictType.AUTO_MERGE;
                return new UpdateNodePositionRes(
                        nodeId,
                        x,
                        y,
                        written.get(),
                        autoMerged
                );
            }

            log.info(
                    "node position CAS retry teamId={} graphId={} nodeId={} userId={} baseVersion={} attempt={}",
                    teamId, graphId, nodeId, userId, baseVersion, attempt
            );
        }

        throw new BaseException(
                BaseResponseStatus.NODE_EDIT_CONFLICT,
                "동시 변경이 반복되어 위치를 저장하지 못했습니다. 최신 그래프를 다시 조회해 주세요."
        );
    }

    private void requireLockOwnership(Long teamId, Long graphId, Long nodeId, Long userId) {
        if (!canvasLockService.isHeldBy(teamId, graphId, nodeId, userId)) {
            throw new BaseException(
                    BaseResponseStatus.NODE_LOCKED,
                    "노드 위치를 변경하려면 유효한 이동 락을 보유해야 합니다."
            );
        }
    }

    private void evictCachesBestEffort(Long graphId) {
        try {
            nodeCacheService.evictPageNodes(graphId);
        } catch (RuntimeException e) {
            log.warn("node position page cache eviction failed graphId={}", graphId, e);
        }

        try {
            graphCacheService.evictGraph(graphId);
        } catch (RuntimeException e) {
            log.warn("node position graph cache eviction failed graphId={}", graphId, e);
        }
    }
}
