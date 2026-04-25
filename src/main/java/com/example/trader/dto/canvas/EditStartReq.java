package com.example.trader.dto.canvas;

import java.util.List;

/**
 * POST /api/teams/{teamId}/graphs/{graphId}/nodes/{nodeId}/edit-start 요청 바디.
 *
 * @param baseVersion 클라이언트가 현재 보고 있는 노드 버전 (충돌 감지 기준)
 * @param fields      편집할 필드 목록 ex) ["subject", "content"]
 */
public record EditStartReq(
        Integer      baseVersion,
        List<String> fields
) {}
