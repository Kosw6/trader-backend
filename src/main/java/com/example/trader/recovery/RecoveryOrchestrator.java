package com.example.trader.recovery;

import com.example.trader.server.ServerStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryOrchestrator {

    private final BroadcastConsumerService broadcastConsumerService;
    private final CatchupConsumerService catchupConsumerService;
    private final RecoveryReadinessManager recoveryReadinessManager;
    private final ServerStateManager serverStateManager;
    private final ReplayStateTracker replayStateTracker;

    public void startBroadcast() {
        replayStateTracker.reset();
        serverStateManager.markDraining(false);
        serverStateManager.markReady(false);

        catchupConsumerService.stopCatchupContainer();
        broadcastConsumerService.startBroadcastContainer();

        serverStateManager.markReady(true);

        log.info("[LIFECYCLE] broadcast mode started state={}", serverStateManager.snapshot());
    }

    public void startRecovery() {
        recoveryReadinessManager.onRecoveryStart();
        broadcastConsumerService.stopBroadcastContainer();
        catchupConsumerService.startCatchupContainer();

        log.info("[RECOVERY] start requested catchupRunning={} broadcastRunning={}",
                catchupConsumerService.isRunning(),
                broadcastConsumerService.isRunning());
    }

    public void completeRecovery() {
        catchupConsumerService.stopCatchupContainer();
        broadcastConsumerService.startBroadcastContainer();
        recoveryReadinessManager.onCatchupCompleted();

        log.info("[RECOVERY] completed -> catch-up OFF / broadcast ON");
    }

    public void startDrain() {
        serverStateManager.markDraining(true);
        log.info("[LIFECYCLE] drain started state={}", serverStateManager.snapshot());
    }

    public void stopDrain() {
        serverStateManager.markDraining(false);
        log.info("[LIFECYCLE] drain stopped state={}", serverStateManager.snapshot());
    }
}