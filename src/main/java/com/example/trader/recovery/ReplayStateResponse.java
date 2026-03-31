package com.example.trader.recovery;

import java.util.Map;

public record ReplayStateResponse(
        int replayCount,
        Map<Integer, Long> targetOffsets,
        Map<Integer, Long> lastConsumedOffsets,
        boolean catchupCompleted
) {}