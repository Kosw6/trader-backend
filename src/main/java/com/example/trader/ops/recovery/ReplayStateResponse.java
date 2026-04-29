package com.example.trader.ops.recovery;

import java.util.Map;

public record ReplayStateResponse(
        int replayCount,
        Map<Integer, Long> targetOffsets,
        Map<Integer, Long> lastConsumedOffsets,
        boolean catchupCompleted
) {
    public static ReplayStateResponse from(ReplayStateTracker tracker) {
        return new ReplayStateResponse(
                tracker.getReplayCount().get(),
                Map.copyOf(tracker.getTargetOffsets()),
                Map.copyOf(tracker.getLastConsumedOffsets()),
                tracker.getCatchupCompleted().get()
        );
    }
}