package com.example.trader.ops.recovery;

import com.example.trader.server.ServerStateManager;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class RecoveryOrchestrator {

    private final BroadcastConsumerService broadcastConsumerService;
    private final CatchupConsumerService catchupConsumerService;
    private final RecoveryReadinessManager recoveryReadinessManager;
    private final ServerStateManager serverStateManager;
    private final ReplayStateTracker replayStateTracker;
    private final ApplicationInfoManager applicationInfoManager;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[LIFECYCLE] application ready → starting broadcast mode");
        startBroadcast();
    }

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
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
        log.info("[LIFECYCLE] drain started state={}", serverStateManager.snapshot());
    }

    public void stopDrain() {
        serverStateManager.markDraining(false);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        log.info("[LIFECYCLE] drain stopped state={}", serverStateManager.snapshot());
    }
}