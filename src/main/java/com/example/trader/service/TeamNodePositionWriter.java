package com.example.trader.service;

import com.example.trader.entity.NodeHistory;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 팀 노드 위치 변경의 DB 원자성 경계.
 *
 * <p>노드 버전 CAS, 변경 이력, RELIABLE Outbox를 같은 트랜잭션에 저장한다.
 * 반환값이 비어 있으면 검증 이후 다른 요청이 먼저 버전을 변경한 것이다.
 */
@Service
@RequiredArgsConstructor
public class TeamNodePositionWriter {

    private static final List<String> POSITION_FIELDS = List.of("x", "y");

    private final NodeRepository nodeRepository;
    private final NodeHistoryRepository nodeHistoryRepository;
    private final TransactionalReliableEventService reliableEventService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<Integer> write(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId,
            int expectedVersion,
            double x,
            double y
    ) {
        int updated = nodeRepository.updatePositionInTeamIfVersion(
                teamId, graphId, nodeId, expectedVersion, x, y
        );
        if (updated == 0) {
            return Optional.empty();
        }

        int newVersion = expectedVersion + 1;
        nodeHistoryRepository.save(NodeHistory.builder()
                .nodeId(nodeId)
                .teamId(teamId)
                .graphId(graphId)
                .version(newVersion)
                .changedBy(userId)
                .changedFields(toJson(POSITION_FIELDS))
                .changedAt(LocalDateTime.now())
                .build());

        RawCursorMessage message = new RawCursorMessage(
                "__CONTROL__",
                "NODE_POSITION_UPDATED",
                teamId,
                graphId,
                userId,
                null,
                nodeId,
                x,
                y,
                System.currentTimeMillis(),
                POSITION_FIELDS,
                null,
                null,
                newVersion
        );

        reliableEventService.stage(
                RealtimeEnvelope.builder()
                        .type(RealtimeType.RELIABLE)
                        .subType(RealtimeSubType.NODE_POSITION_UPDATED)
                        .teamId(teamId)
                        .graphId(graphId)
                        .payload(message)
                        .build()
        );

        return Optional.of(newVersion);
    }

    private String toJson(List<String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize changed position fields", e);
        }
    }
}
