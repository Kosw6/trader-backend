package com.example.trader.health;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

//TODO:테스트용 운영시 제거
@RestController
@RequiredArgsConstructor
public class HealthTestController {

    @PostMapping("/internal/test/health/down")
    public void down() {
        ManualDownHealthIndicator.setDown(true);
    }

    @PostMapping("/internal/test/health/up")
    public void up() {
        ManualDownHealthIndicator.setDown(false);
    }
}