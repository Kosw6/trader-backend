package com.example.trader.policy;

import com.example.trader.controller.loginController;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes={PolicyController.class,AiTransferController.class,loginController.class})
public class PolicyExceptionHandler {
    private static final Set<String> CODES=Set.of("POLICY_NOT_PUBLISHED","CONSENT_REQUIRED","POLICY_CHANGED","POLICY_RELEASE_CONFLICT");
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> rejected(ResponseStatusException ex) {
        String code=ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("code",code!=null && CODES.contains(code)?code:"REQUEST_REJECTED"));
    }
}
