package com.example.trader.common;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.exception.BaseException;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.httpresponse.BaseResponseStatus;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

@Hidden
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NodeConflictException.class)
    public ResponseEntity<ConflictResult> handleNodeConflict(NodeConflictException ex) {
        putErrorMdc(ex.getStatus());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ex.getResult());
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(BaseException ex) {
        BaseResponseStatus status = ex.getStatus();
        putErrorMdc(status);

        Map<String, Object> response = new HashMap<>();
        response.put("traceId", MDC.get("traceId"));
        response.put("status", status.getCode());
        response.put("domain", status.getDomain());
        response.put("errorCode", status.getErrorCode());
        response.put("severity", status.getSeverity().name());
        response.put("message", ex.getMessage());

        log.error("handled base exception");

        return ResponseEntity.status(status.getCode()).body(response);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex) {
        BaseResponseStatus status = BaseResponseStatus.INVALID_REQUEST;
        putErrorMdc(status);

        Map<String, Object> response = new HashMap<>();
        response.put("traceId", MDC.get("traceId"));
        response.put("status", 404);
        response.put("domain", "common");
        response.put("errorCode", "NO_HANDLER_FOUND");
        response.put("severity", "LOW");
        response.put("message", "요청하신 경로가 존재하지 않습니다.");
        response.put("path", ex.getRequestURL());

        log.warn("no handler found");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("traceId", MDC.get("traceId"));
        response.put("status", 400);
        response.put("domain", "common");
        response.put("errorCode", "ILLEGAL_ARGUMENT");
        response.put("severity", "LOW");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());

        MDC.put("event", "API_ERROR");
        MDC.put("domain", "common");
        MDC.put("errorCode", "ILLEGAL_ARGUMENT");
        MDC.put("severity", "LOW");
        MDC.put("httpStatus", "400");

        log.warn("illegal argument exception");

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));

        Map<String, Object> response = new HashMap<>();
        response.put("traceId", MDC.get("traceId"));
        response.put("status", 400);
        response.put("domain", "common");
        response.put("errorCode", "VALIDATION_FAILED");
        response.put("severity", "LOW");
        response.put("errors", errors);

        MDC.put("event", "API_ERROR");
        MDC.put("domain", "common");
        MDC.put("errorCode", "VALIDATION_FAILED");
        MDC.put("severity", "LOW");
        MDC.put("httpStatus", "400");

        log.warn("validation failed");

        return ResponseEntity.badRequest().body(response);
    }

    private void putErrorMdc(BaseResponseStatus status) {
        MDC.put("event", "API_ERROR");
        MDC.put("domain", status.getDomain());
        MDC.put("errorCode", status.getErrorCode());
        MDC.put("severity", status.getSeverity().name());
        MDC.put("httpStatus", String.valueOf(status.getCode()));
    }
}