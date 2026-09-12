package com.example.trader.common;

import com.example.trader.exception.InvalidNodeChangeRequestException;
import com.example.trader.exception.NodeChangeErrorCode;
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
}
