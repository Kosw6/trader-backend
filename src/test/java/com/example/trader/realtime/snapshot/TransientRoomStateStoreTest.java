package com.example.trader.realtime.snapshot;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransientRoomStateStoreTest {

    @Test
    void singleModeSnapshotRepresentsCurrentLockStateAndRevision() {
        TransientRoomStateStore store = new TransientRoomStateStore(
                mock(StringRedisTemplate.class),
                mock(RedisHealthState.class),
                new ObjectMapper(),
                "single"
        );

        RealtimeEnvelope acquired = store.record(lockEvent(RealtimeSubType.LOCK_ACQUIRED));
        TransientRoomSnapshot acquiredSnapshot = store.snapshot(1L, 2L);

        assertThat(acquired.getTransientRevision()).isEqualTo(1L);
        assertThat(acquiredSnapshot.revision()).isEqualTo(1L);
        assertThat(acquiredSnapshot.completeness()).isEqualTo(SnapshotCompleteness.COMPLETE);
        assertThat(acquiredSnapshot.states()).hasSize(1);
        assertThat(acquiredSnapshot.states().get(0).event().getSubType())
                .isEqualTo(RealtimeSubType.LOCK_ACQUIRED);
        assertThat(acquiredSnapshot.states().get(0).expiresAt()).isGreaterThan(System.currentTimeMillis());

        RealtimeEnvelope released = store.record(lockEvent(RealtimeSubType.LOCK_RELEASED));
        TransientRoomSnapshot releasedSnapshot = store.snapshot(1L, 2L);

        assertThat(released.getTransientRevision()).isEqualTo(2L);
        assertThat(releasedSnapshot.revision()).isEqualTo(2L);
        assertThat(releasedSnapshot.states()).isEmpty();
    }

    private RealtimeEnvelope lockEvent(RealtimeSubType subType) {
        return RealtimeEnvelope.builder()
                .eventId(subType.name())
                .type(RealtimeType.TRANSIENT)
                .subType(subType)
                .teamId(1L)
                .graphId(2L)
                .payload(Map.of("nodeId", 3L, "userId", 4L, "lockTtlMs", 5_000L))
                .build();
    }
}
