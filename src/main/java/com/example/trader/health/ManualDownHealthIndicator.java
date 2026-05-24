package com.example.trader.health;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

//TODO:테스트용 운영시 제거
@Component
public class ManualDownHealthIndicator implements HealthIndicator {

    private static final AtomicBoolean DOWN = new AtomicBoolean(false);

    public static void setDown(boolean down) {
        DOWN.set(down);
    }

    @Override
    public Health health() {
        if (DOWN.get()) {
            return Health.down()
                    .withDetail("reason", "manual unhealthy test")
                    .build();
        }

        return Health.up().build();
    }
}