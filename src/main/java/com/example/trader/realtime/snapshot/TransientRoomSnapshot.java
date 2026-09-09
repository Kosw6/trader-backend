package com.example.trader.realtime.snapshot;

import java.util.List;

public record TransientRoomSnapshot(
        String type,
        Long teamId,
        Long graphId,
        Long revision,
        long generatedAt,
        SnapshotCompleteness completeness,
        List<TransientSnapshotState> states
) {
    public TransientRoomSnapshot {
        if (states == null) {
            states = List.of();
        }
    }

    public static TransientRoomSnapshot of(
            Long teamId,
            Long graphId,
            Long revision,
            SnapshotCompleteness completeness,
            List<TransientSnapshotState> states
    ) {
        return new TransientRoomSnapshot(
                "TRANSIENT_SNAPSHOT",
                teamId,
                graphId,
                revision,
                System.currentTimeMillis(),
                completeness,
                states
        );
    }
}
