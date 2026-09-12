package com.example.trader.service.event;

import java.util.List;

/**
 * 팀 Node 변경의 DB 트랜잭션이 커밋된 뒤에만 처리할 파생 작업의 입력이다.
 */
public record TeamNodeChangeCommittedEvent(
        Long teamId,
        Long graphId,
        Long nodeId,
        Long userId,
        int version,
        List<String> changedFields
) {
    public TeamNodeChangeCommittedEvent {
        changedFields = List.copyOf(changedFields);
    }
}
