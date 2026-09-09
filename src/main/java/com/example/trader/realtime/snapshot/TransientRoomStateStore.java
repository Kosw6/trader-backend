package com.example.trader.realtime.snapshot;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방별 TRANSIENT 현재 상태와 revision을 관리한다.
 * 이벤트 이력이 아니라 활성 Lock/Edit 상태만 보관한다.
 */
@Slf4j
@Component
public class TransientRoomStateStore {

    private static final Duration EDIT_TTL = Duration.ofMinutes(10);
    private static final Duration REDIS_KEY_TTL = Duration.ofHours(24);

    private static final DefaultRedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>("""
            local revision = redis.call('INCR', KEYS[3])
            if ARGV[1] == 'UPSERT' then
                redis.call('HSET', KEYS[1], ARGV[2], ARGV[3])
                redis.call('HSET', KEYS[2], ARGV[2], ARGV[4])
            elseif ARGV[1] == 'REMOVE' then
                redis.call('HDEL', KEYS[1], ARGV[2])
                redis.call('HDEL', KEYS[2], ARGV[2])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[5])
            redis.call('EXPIRE', KEYS[2], ARGV[5])
            redis.call('EXPIRE', KEYS[3], ARGV[5])
            return revision
            """, Long.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SNAPSHOT_SCRIPT = new DefaultRedisScript<>("""
            local revision = redis.call('GET', KEYS[3]) or '0'
            local values = redis.call('HGETALL', KEYS[1])
            local result = { revision }
            for i = 1, #values, 2 do
                local field = values[i]
                local expiresAt = redis.call('HGET', KEYS[2], field)
                if expiresAt and tonumber(expiresAt) > tonumber(ARGV[1]) then
                    table.insert(result, field)
                    table.insert(result, values[i + 1])
                    table.insert(result, expiresAt)
                else
                    redis.call('HDEL', KEYS[1], field)
                    redis.call('HDEL', KEYS[2], field)
                end
            end
            return result
            """, List.class);

    private final StringRedisTemplate redis;
    private final RedisHealthState redisHealthState;
    private final ObjectMapper objectMapper;
    private final boolean multiMode;
    private final ConcurrentHashMap<String, LocalRoomState> localRooms = new ConcurrentHashMap<>();
    /** Redis에 반영하지 못한 상태 변경이 있었던 방은 복구 후에도 즉시 COMPLETE라고 주장하지 않는다. */
    private final ConcurrentHashMap<String, Long> degradedUntilByRoom = new ConcurrentHashMap<>();

    public TransientRoomStateStore(
            StringRedisTemplate redis,
            RedisHealthState redisHealthState,
            ObjectMapper objectMapper,
            @Value("${app.mode:single}") String appMode
    ) {
        this.redis = redis;
        this.redisHealthState = redisHealthState;
        this.objectMapper = objectMapper;
        this.multiMode = "multi".equalsIgnoreCase(appMode);
    }

    public RealtimeEnvelope record(RealtimeEnvelope envelope) {
        StateMutation mutation = mutationOf(envelope);
        Long localRevision = applyLocal(roomKey(envelope), mutation, envelope.getTransientRevision());

        if (!multiMode) {
            RealtimeEnvelope recorded = envelope.toBuilder().transientRevision(localRevision).build();
            applyLocal(roomKey(envelope), mutationOf(recorded), localRevision);
            return recorded;
        }
        if (!redisHealthState.isAvailable()) {
            markDegraded(roomKey(envelope));
            return envelope.toBuilder().transientRevision(null).build();
        }

        try {
            String json = mutation.action() == Action.UPSERT
                    ? objectMapper.writeValueAsString(envelope)
                    : "";
            Long revision = redis.execute(
                    UPDATE_SCRIPT,
                    redisKeys(envelope.getTeamId(), envelope.getGraphId()),
                    mutation.action().name(),
                    mutation.field(),
                    json,
                    String.valueOf(mutation.expiresAt()),
                    String.valueOf(REDIS_KEY_TTL.toSeconds())
            );
            redisHealthState.markUp();
            RealtimeEnvelope recorded = envelope.toBuilder().transientRevision(revision).build();
            applyLocal(roomKey(envelope), mutationOf(recorded), revision);
            return recorded;
        } catch (Exception e) {
            redisHealthState.markDown();
            markDegraded(roomKey(envelope));
            log.warn("[TRANSIENT-STATE] Redis update failed. room={}, reason={}",
                    roomKey(envelope), e.getMessage());
            return envelope.toBuilder().transientRevision(null).build();
        }
    }

    /** Redis에서 받은 상태를 로컬 degraded snapshot에도 반영한다. */
    public void observe(RealtimeEnvelope envelope) {
        applyLocal(roomKey(envelope), mutationOf(envelope), envelope.getTransientRevision());
    }

    public TransientRoomSnapshot snapshot(Long teamId, Long graphId) {
        String roomKey = roomKey(teamId, graphId);
        boolean degraded = isDegraded(roomKey);
        if (multiMode && redisHealthState.isAvailable()) {
            try {
                TransientRoomSnapshot snapshot = readRedisSnapshot(teamId, graphId);
                redisHealthState.markUp();
                if (degraded) {
                    LocalRoomState localRoom = localRooms.get(roomKey);
                    LocalSnapshot local = localRoom == null ? LocalSnapshot.empty() : localRoom.snapshot();
                    return TransientRoomSnapshot.of(
                            teamId,
                            graphId,
                            null,
                            SnapshotCompleteness.DEGRADED,
                            merge(snapshot.states(), local.states())
                    );
                }
                return snapshot;
            } catch (Exception e) {
                redisHealthState.markDown();
                log.warn("[TRANSIENT-SNAPSHOT] Redis read failed. teamId={}, graphId={}, reason={}",
                        teamId, graphId, e.getMessage());
            }
        }

        LocalRoomState room = localRooms.get(roomKey);
        LocalSnapshot local = room == null ? LocalSnapshot.empty() : room.snapshot();
        Long revision = !multiMode ? local.revision() : null;
        return TransientRoomSnapshot.of(
                teamId,
                graphId,
                revision,
                multiMode ? SnapshotCompleteness.DEGRADED : SnapshotCompleteness.COMPLETE,
                local.states()
        );
    }

    private List<TransientSnapshotState> merge(
            List<TransientSnapshotState> shared,
            List<TransientSnapshotState> local
    ) {
        Map<String, TransientSnapshotState> merged = new LinkedHashMap<>();
        shared.forEach(state -> merged.put(stateKey(state), state));
        local.forEach(state -> merged.put(stateKey(state), state));
        return List.copyOf(merged.values());
    }

    private String stateKey(TransientSnapshotState state) {
        return mutationOf(state.event()).field();
    }

    private void markDegraded(String roomKey) {
        degradedUntilByRoom.merge(
                roomKey,
                System.currentTimeMillis() + EDIT_TTL.toMillis(),
                Math::max
        );
    }

    private boolean isDegraded(String roomKey) {
        Long until = degradedUntilByRoom.get(roomKey);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        degradedUntilByRoom.remove(roomKey, until);
        return false;
    }

    @SuppressWarnings("unchecked")
    private TransientRoomSnapshot readRedisSnapshot(Long teamId, Long graphId) throws Exception {
        List<Object> raw = (List<Object>) redis.execute(
                SNAPSHOT_SCRIPT,
                redisKeys(teamId, graphId),
                String.valueOf(System.currentTimeMillis())
        );
        if (raw == null || raw.isEmpty()) {
            return TransientRoomSnapshot.of(teamId, graphId, 0L, SnapshotCompleteness.COMPLETE, List.of());
        }

        long revision = Long.parseLong(asString(raw.get(0)));
        List<TransientSnapshotState> states = new ArrayList<>();
        for (int i = 1; i + 2 < raw.size(); i += 3) {
            RealtimeEnvelope event = objectMapper.readValue(asString(raw.get(i + 1)), RealtimeEnvelope.class);
            states.add(new TransientSnapshotState(event, Long.parseLong(asString(raw.get(i + 2)))));
        }
        return TransientRoomSnapshot.of(teamId, graphId, revision, SnapshotCompleteness.COMPLETE, states);
    }

    private Long applyLocal(String roomKey, StateMutation mutation, Long observedRevision) {
        LocalRoomState room = localRooms.computeIfAbsent(roomKey, ignored -> new LocalRoomState());
        return room.apply(mutation, observedRevision);
    }

    @SuppressWarnings("unchecked")
    private StateMutation mutationOf(RealtimeEnvelope envelope) {
        RealtimeSubType subType = envelope.getSubType();
        Map<String, Object> payload = envelope.getPayload() == null
                ? Map.of()
                : objectMapper.convertValue(envelope.getPayload(), Map.class);
        Long nodeId = longValue(payload.get("nodeId"));
        Long userId = longValue(payload.get("userId"));
        long now = System.currentTimeMillis();

        if (subType == RealtimeSubType.LOCK_ACQUIRED && nodeId != null) {
            Long ttl = longValue(payload.get("lockTtlMs"));
            return new StateMutation(Action.UPSERT, "LOCK:" + nodeId, envelope,
                    now + (ttl == null ? 5_000L : ttl));
        }
        if (subType == RealtimeSubType.LOCK_RELEASED && nodeId != null) {
            return new StateMutation(Action.REMOVE, "LOCK:" + nodeId, null, 0L);
        }
        if (subType == RealtimeSubType.EDIT_START && nodeId != null && userId != null) {
            return new StateMutation(Action.UPSERT, "EDIT:" + nodeId + ":" + userId,
                    envelope, now + EDIT_TTL.toMillis());
        }
        if ((subType == RealtimeSubType.EDIT_END || subType == RealtimeSubType.EDIT_CANCEL)
                && nodeId != null && userId != null) {
            return new StateMutation(Action.REMOVE, "EDIT:" + nodeId + ":" + userId, null, 0L);
        }
        return new StateMutation(Action.NONE, "_", null, 0L);
    }

    private List<String> redisKeys(Long teamId, Long graphId) {
        String prefix = "canvas:transient:" + teamId + ":" + graphId;
        return List.of(prefix + ":states", prefix + ":expiries", prefix + ":revision");
    }

    private String roomKey(RealtimeEnvelope envelope) {
        return roomKey(envelope.getTeamId(), envelope.getGraphId());
    }

    private String roomKey(Long teamId, Long graphId) {
        return "team:%d:graph:%d".formatted(teamId, graphId);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : String.valueOf(value);
    }

    private enum Action { UPSERT, REMOVE, NONE }

    private record StateMutation(Action action, String field, RealtimeEnvelope envelope, long expiresAt) {}

    private static final class LocalRoomState {
        private final Map<String, StoredState> states = new LinkedHashMap<>();
        private long revision;

        synchronized long apply(StateMutation mutation, Long observedRevision) {
            revision = observedRevision == null ? revision + 1 : Math.max(revision, observedRevision);
            if (mutation.action() == Action.UPSERT) {
                states.put(mutation.field(), new StoredState(mutation.envelope(), mutation.expiresAt()));
            } else if (mutation.action() == Action.REMOVE) {
                states.remove(mutation.field());
            }
            return revision;
        }

        synchronized LocalSnapshot snapshot() {
            long now = System.currentTimeMillis();
            states.values().removeIf(state -> state.expiresAt() <= now);
            List<TransientSnapshotState> snapshotStates = states.values().stream()
                    .map(state -> new TransientSnapshotState(state.envelope(), state.expiresAt()))
                    .toList();
            return new LocalSnapshot(revision, snapshotStates);
        }
    }

    private record StoredState(RealtimeEnvelope envelope, long expiresAt) {}
    private record LocalSnapshot(long revision, List<TransientSnapshotState> states) {
        private static LocalSnapshot empty() {
            return new LocalSnapshot(0L, List.of());
        }
    }
}
