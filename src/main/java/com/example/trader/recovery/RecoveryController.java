package com.example.trader.recovery;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/lifecycle")
public class RecoveryController {

    private final RecoveryOrchestrator recoveryOrchestrator;
    private final ReplayStateTracker replayStateTracker;

    @PostMapping("/start-broadcast")
    public ResponseEntity<Void> startBroadcast() {
        recoveryOrchestrator.startBroadcast();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/start-recovery")
    public ResponseEntity<Void> startRecovery() {
        recoveryOrchestrator.startRecovery();
        return ResponseEntity.ok().build();
    }

    /**
     * 수동 complete는 디버그/비상용
     * 운영 흐름은 catch-up 내부 자동 완료 권장
     */
    @PostMapping("/complete")
    public ResponseEntity<Void> completeRecovery() {
        recoveryOrchestrator.completeRecovery();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/replay-state")
    public ReplayStateResponse replayState() {
        return new ReplayStateResponse(
                replayStateTracker.getReplayCount().get(),
                Map.copyOf(replayStateTracker.getTargetOffsets()),
                Map.copyOf(replayStateTracker.getLastConsumedOffsets()),
                replayStateTracker.isCatchupCompleted()
        );
    }

    public record ReplayStateResponse(
            int replayCount,
            Map<Integer, Long> targetOffsets,
            Map<Integer, Long> lastConsumedOffsets,
            boolean catchupCompleted
    ) {}
}