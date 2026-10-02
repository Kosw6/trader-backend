package com.example.trader.controller;

import com.example.trader.security.provider.JwtKeyRing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {
    private final JwtKeyRing keys;
    @GetMapping("/api/auth/jwks")
    public ResponseEntity<Map<String, Object>> publicKeys() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(keys.jwks());
    }
}
