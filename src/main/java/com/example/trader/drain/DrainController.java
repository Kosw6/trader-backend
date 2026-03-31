package com.example.trader.drain;

import com.example.trader.drain.dto.DrainRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/drain")
public class DrainController {

    private final DrainService drainService;

    @Value("${INTERNAL_API_TOKEN:token}")
    private String internalToken;

    @PostMapping
    public ResponseEntity<Void> startDrain(@RequestHeader("X-Internal-Token") String token,
                                           @RequestBody DrainRequest request) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        drainService.startDrain(request);
        return ResponseEntity.ok().build();
    }
}