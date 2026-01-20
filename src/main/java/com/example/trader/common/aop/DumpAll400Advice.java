package com.example.trader.common.aop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class DumpAll400Advice {
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> any(Exception e, HttpServletRequest req) {
        e.printStackTrace(); // 🔎 파일/라인 바로 확인
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.toString(), "path", req.getRequestURI()));
    }
}
