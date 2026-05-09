package com.example.trader.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인스턴스 간 HTTP relay health check 전용 엔드포인트.
 * DefaultHttpHealthChecker가 REALTIME_HTTP_HEALTH_URL로 폴링하며 HTTP relay 활성 여부를 판단한다.
 * /internal/** 경로는 SecurityConfig에서 permitAll 처리됨.
 */
@RestController
@RequestMapping("/internal")
public class InternalHealthController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
