package com.example.trader.emailverification;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/login/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService service;
    public EmailVerificationController(EmailVerificationService service) { this.service = service; }
    public record Request(String email) {}
    public record Confirm(String email, String challengeId, String code) {}

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("required", service.enabled()));
    }
    @PostMapping("/requests")
    public ResponseEntity<?> request(@RequestBody Request request, HttpServletRequest servlet) {
        // Do not trust client-supplied X-Forwarded-For. Configure trusted proxies at the server.
        return ResponseEntity.accepted().header("Cache-Control", "no-store")
                .body(service.request(request.email(), servlet.getRemoteAddr()));
    }
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Confirm request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(service.confirm(request.email(), request.challengeId(), request.code()));
    }
}
