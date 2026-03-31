package com.example.trader.recovery;

import com.example.trader.server.ServerStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryReadinessManager {

    private final ServerStateManager serverStateManager;

    public void onRecoveryStart() {
        serverStateManager.markReady(false);
        serverStateManager.markDraining(false);
        log.info("[RECOVERY] started state={}", serverStateManager.snapshot());
    }

    public void onCatchupCompleted() {
        serverStateManager.markReady(true);
        log.info("[RECOVERY] catch-up completed -> ready=true state={}", serverStateManager.snapshot());
    }
}