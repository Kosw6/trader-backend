package com.example.trader.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.UUID;

@Order(-10)
@RestControllerAdvice(assignableTypes = AiSourceController.class)
public class AiExceptionHandler {
    @ExceptionHandler(AiApiException.class)
    public ResponseEntity<?> handle(AiApiException error, HttpServletRequest request) {
        Object attr = request.getAttribute(AiRequestContext.ATTRIBUTE);
        String id = attr instanceof AiRequestContext ctx ? ctx.requestId() : UUID.randomUUID().toString();
        return ResponseEntity.status(error.status).header("Cache-Control", "no-store")
                .body(Map.of("code", error.code, "message", error.getMessage(), "requestId", id));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unavailable(Exception error, HttpServletRequest request) {
        return handle(new AiApiException(503, "SOURCE_READ_UNAVAILABLE"), request);
    }
}
