package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeChangePlan;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamNodeConflictSnapshotService {

    private final NodeRepository nodeRepository;
    private final NodeChangeRequestValidator changeRequestValidator;
    private final NodeConflictValidator conflictValidator;

    /** 두 번째 CAS 실패 후 최신 Node와 같은 스냅샷의 409 응답을 만든다. */
    @Transactional(readOnly = true)
    public ConflictResult resolveAfterRetryExhausted(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId,
            UpdateTeamNodeContentRequest request
    ) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));
        Set<Long> replaceableNoteIds = node.getNoteLinks().stream()
                .filter(link -> link.getNote().getUser().getId().equals(userId))
                .map(link -> link.getNote().getId())
                .collect(Collectors.toSet());
        NodeChangePlan plan = changeRequestValidator.validateTeam(
                request,
                node,
                replaceableNoteIds
        );

        if (!plan.isNoOp()) {
            ConflictResult conflict = conflictValidator.validateContent(
                    teamId,
                    graphId,
                    nodeId,
                    node,
                    request.getBaseVersion(),
                    plan.effectiveChange()
            );
            if (conflict.isConflict()) {
                return conflict;
            }
        }

        return conflictValidator.concurrentContentUpdate(
                node,
                request.getBaseVersion(),
                plan.requestedChange()
        );
    }
}
