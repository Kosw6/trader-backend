package com.example.trader.emailverification;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Order(-10)
@RestControllerAdvice
public class EmailVerificationExceptionHandler {
    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<Map<String, String>> rejected(EmailVerificationException ex) {
        var response = ResponseEntity.status(ex.status()).header("Cache-Control", "no-store");
        if (ex.status().value() == 429) response.header("Retry-After", "60");
        return response.body(Map.of("code", ex.code()));
    }
}
