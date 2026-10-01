package com.example.trader.common;

import com.example.trader.exception.InvalidNodeChangeRequestException;
import com.example.trader.exception.NodeChangeErrorCode;
import com.example.trader.entity.NodeHistory;
import com.example.trader.exception.NodeHistoryCorruptedException;
import com.example.trader.exception.NodeHistoryCorruptionReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerNodeChangeTest {

    @Test
    @DisplayName("Node 변경 계약 오류는 세부 errorCode를 보존한 HTTP 400으로 반환")
    void preserve_node_change_error_code() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH",
                "/api/teams/1/graphs/2/nodes/3"
        );

        ResponseEntity<Map<String, Object>> response = handler.handleInvalidNodeChange(
                new InvalidNodeChangeRequestException(NodeChangeErrorCode.DIRTY_FIELDS_EMPTY),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody())
                .containsEntry("domain", "canvas")
                .containsEntry("errorCode", "DIRTY_FIELDS_EMPTY")
                .containsEntry("status", 400);
    }

    @Test
    @DisplayName("Node History 손상은 한글 메시지와 안정적인 HTTP 500 계약으로 반환")
    void preserve_node_history_corruption_contract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NodeHistory history = NodeHistory.builder()
                .id(100L)
                .nodeId(30L)
                .version(4)
                .changedFields("[]")
                .build();

        ResponseEntity<Map<String, Object>> response = handler.handleCustomException(
                new NodeHistoryCorruptedException(
                        history,
                        NodeHistoryCorruptionReason.EMPTY_CHANGED_FIELDS
                )
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody())
                .containsEntry("domain", "canvas")
                .containsEntry("errorCode", "NODE_HISTORY_CORRUPTED")
                .containsEntry("severity", "HIGH")
                .containsEntry("message", "변경 이력을 해석할 수 없어 충돌을 검증하지 못했습니다.")
                .containsEntry("status", 500);
    }
}
