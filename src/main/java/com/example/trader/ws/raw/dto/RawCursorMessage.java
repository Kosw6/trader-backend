package com.example.trader.ws.raw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * WebSocket 메시지 DTO.
 *
 * <p>type 별 사용 필드:
 * <pre>
 * CURSOR        : type, teamId, graphId, userId, nickName, x, y, sentAt
 * DRAG_PREVIEW  : type, teamId, graphId, userId, nickName, nodeId, x, y, sentAt
 * __CONTROL__   : type, subType, teamId, graphId, userId, nickName, nodeId, (fields, baseVersion)
 *   subType:
 *     LOCK_ACQUIRE    - 클라 → 서버: 락 요청
 *     LOCK_ACQUIRED   - 서버 → 룸:   락 획득 알림
 *     LOCK_DENIED     - 서버 → 요청자: 락 거부 (lockedBy = 점유자 userId)
 *     LOCK_RELEASE    - 클라 → 서버: 락 해제
 *     LOCK_RELEASED   - 서버 → 룸:   락 해제 알림
 *     LOCK_KEEPALIVE  - 클라 → 서버: TTL 갱신 (드래그 중 주기적 전송)
 *     EDIT_START      - 클라 → 서버: 편집 시작 (fields, baseVersion 필수)
 *     EDIT_END        - 클라 → 서버: 편집 종료
 * </pre>
 *
 * <p>null 필드는 JSON 직렬화 시 제외 (@JsonInclude NON_NULL).
 * CURSOR/DRAG 메시지에서 subType/fields/baseVersion 이 불필요하게 노출되지 않음.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RawCursorMessage(
        String       type,
        String       subType,       // __CONTROL__ 세부 구분
        Long         teamId,
        Long         graphId,
        Long         userId,
        String       nickName,
        Long         nodeId,        // DRAG_PREVIEW, CONTROL 이벤트용
        double       x,
        double       y,
        long         sentAt,
        List<String> fields,        // EDIT_START: 편집 중인 필드 목록
        Integer      baseVersion    // EDIT_START: 편집 시작 당시 노드 버전
) {}
