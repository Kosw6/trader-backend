package com.example.trader.common;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.exception.BaseException;
import com.example.trader.exception.NodeConflictException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

//스웨거 최신 버전과 스프링부트 최신버전 불일치 @RestControllerAdvice
//글로벌 에러체크
@Hidden
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 노드 편집 충돌 — 409 Conflict + ConflictResult (diff 포함).
     * 클라이언트는 conflictingFields / currentValues / incomingValues 를 보고
     * force=true 로 재요청(강제 저장) 또는 취소를 선택.
     */
    @ExceptionHandler(NodeConflictException.class)
    public ResponseEntity<ConflictResult> handleNodeConflict(NodeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getResult());
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(BaseException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("errorCode", ex.getStatus().getCode());
        response.put("message", ex.getStatus().getMessage());
        log.error("handleCustomException:",response.get("errorCode"));
        return ResponseEntity.status(ex.getStatus().getCode()).body(response);
    }
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 404);
        errorResponse.put("error", "Not Found");
        errorResponse.put("message", "요청하신 경로가 존재하지 않습니다.");
        errorResponse.put("path", ex.getRequestURL());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", 400);
        errorResponse.put("error", "Bad Request");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("path", request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errors);
    }
}
