package com.example.trader.realtime.snapshot;

import com.example.trader.realtime.message.RealtimeEnvelope;

public record TransientSnapshotState(
        RealtimeEnvelope event,
        long expiresAt
) {
}
