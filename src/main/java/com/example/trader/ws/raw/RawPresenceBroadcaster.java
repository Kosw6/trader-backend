package com.example.trader.ws.raw;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.realtime.snapshot.TransientRoomStateStore;
import com.example.trader.ws.common.PresenceBatch;
import com.example.trader.ws.common.RoomPresenceCoalescer;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.concurrent.*;

@Slf4j
@Component
public class RawPresenceBroadcaster {

    private final CanvasSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final TransientRoomStateStore transientStateStore;

    private final RoomPresenceCoalescer<RawCursorMessage> presenceCoalescer =
            new RoomPresenceCoalescer<>();

    private final RoomRealtimeBuffer realtimeBuffer;

    public RawPresenceBroadcaster(
            CanvasSessionRegistry registry,
            ObjectMapper objectMapper,
            TransientRoomStateStore transientStateStore,
            @Value("${realtime.queue.reliable-capacity:5000}") int reliableCapacity,
            @Value("${realtime.queue.transient-capacity:5000}") int transientCapacity,
            @Value("${realtime.queue.max-drain-per-flush:2000}") int maxDrainPerFlush
    ) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.transientStateStore = transientStateStore;
        this.realtimeBuffer = new RoomRealtimeBuffer(
                reliableCapacity,
                transientCapacity,
                maxDrainPerFlush
        );
    }

    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "raw-presence-flusher");
        t.setDaemon(true);
        return t;
    });

    @Value("${RAW_FLUSH_PERIOD:50}")
    long period;

    @Value("${RAW_FLUSH_INITIALDELAY:1}")
    long initialDelay;

    @PostConstruct
    public void start() {
        if (period <= 0) throw new IllegalArgumentException("RAW_FLUSH_PERIOD must be > 0, was " + period);
        if (initialDelay < 0) throw new IllegalArgumentException("RAW_FLUSH_INITIALDELAY must be >= 0, was " + initialDelay);

        flusher.scheduleAtFixedRate(this::flushAllRoomsSafe, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    public void publishLatest(String roomKey, String key, RawCursorMessage msg) {
        if (registry.size(roomKey) == 0) return;
        presenceCoalescer.publishLatest(roomKey, key, msg);
    }

    public void enqueueReliable(String roomKey, RealtimeEnvelope envelope) {
        if (registry.size(roomKey) == 0) return;
        realtimeBuffer.offerReliable(roomKey, envelope);
    }

    public void enqueueTransient(String roomKey, RealtimeEnvelope envelope) {
        if (registry.size(roomKey) == 0) return;
        realtimeBuffer.offerTransient(roomKey, envelope);
    }

    private void flushAllRoomsSafe() {
        try {
            var roomKeys = new HashSet<>(registry.roomKeysSnapshot());
            roomKeys.addAll(realtimeBuffer.roomKeysSnapshot());
            roomKeys.addAll(presenceCoalescer.roomKeysSnapshot());
            for (String roomKey : roomKeys) {
                flushRoom(roomKey);
            }
        } catch (Exception e) {
            log.warn("[RAW] flushAllRoomsSafe error: {}", e.toString());
        }
    }

    private void flushRoom(String roomKey) {
        var sessions = registry.snapshot(roomKey);
        if (sessions.isEmpty()) {
            clearRoomBuffers(roomKey);
            return;
        }

        RoomRealtimeBuffer.Drain drain = realtimeBuffer.drain(roomKey);
        RoomIds room = parseRoomKey(roomKey);

        if (drain.persistentResyncRequired()) {
            fanout(sessions, roomKey, toText(persistentResyncEnvelope(room)));
        }

        if (drain.transientSnapshotRequired()) {
            fanout(sessions, roomKey, toText(transientSnapshotEnvelope(room)));
        }

        for (RealtimeEnvelope envelope : drain.reliableEvents()) {
            fanout(sessions, roomKey, toText(envelope));
        }

        for (RealtimeEnvelope envelope : drain.transientEvents()) {
            fanout(sessions, roomKey, toText(envelope));
        }

        var latest = presenceCoalescer.drainLatestIfDirty(roomKey);
        if (!latest.isEmpty()) {
            PresenceBatch batch = makeBatch(roomKey, latest);
            if (batch != null) {
                fanout(sessions, roomKey, toText(batch));
            }
        }
    }

    private RealtimeEnvelope persistentResyncEnvelope(RoomIds room) {
        return RealtimeEnvelope.builder()
                .type(RealtimeType.RELIABLE)
                .subType(RealtimeSubType.RELIABLE_RESYNC_REQUIRED)
                .teamId(room.teamId())
                .graphId(room.graphId())
                .payload(Map.of(
                        "type", "__CONTROL__",
                        "subType", "RELIABLE_RESYNC_REQUIRED"
                ))
                .build()
                .ensureEventId();
    }

    public RealtimeEnvelope transientSnapshotEnvelope(Long teamId, Long graphId) {
        return RealtimeEnvelope.builder()
                .type(RealtimeType.TRANSIENT)
                .subType(RealtimeSubType.TRANSIENT_SNAPSHOT)
                .teamId(teamId)
                .graphId(graphId)
                .payload(transientStateStore.snapshot(teamId, graphId))
                .build()
                .ensureEventId();
    }

    private RealtimeEnvelope transientSnapshotEnvelope(RoomIds room) {
        return transientSnapshotEnvelope(room.teamId(), room.graphId());
    }

    private RoomIds parseRoomKey(String roomKey) {
        String[] parts = roomKey.split(":");
        return new RoomIds(Long.valueOf(parts[1]), Long.valueOf(parts[3]));
    }

    private void clearRoomBuffers(String roomKey) {
        realtimeBuffer.clearRoom(roomKey);
        presenceCoalescer.clearRoom(roomKey);
    }

    private TextMessage toText(Object o) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(o));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void fanout(List<WebSocketSession> sessions, String roomKey, TextMessage msg) {
        for (WebSocketSession s : sessions) {
            if (s == null) continue;
            if (!s.isOpen()) {
                registry.leave(roomKey, s);
                continue;
            }
            try {
                s.sendMessage(msg);
            } catch (Exception ex) {
                registry.leave(roomKey, s);
            }
        }
    }

    private PresenceBatch makeBatch(String roomKey, java.util.Collection<RawCursorMessage> latest) {
        String[] parts = roomKey.split(":");
        Long teamId = Long.valueOf(parts[1]);
        Long graphId = Long.valueOf(parts[3]);

        var cursorItems = new java.util.ArrayList<PresenceBatch.CursorItem>();
        var dragItems = new java.util.ArrayList<PresenceBatch.DragItem>();

        for (RawCursorMessage m : latest) {
            if ("CURSOR".equals(m.type())) {
                cursorItems.add(new PresenceBatch.CursorItem(
                        m.userId(), m.nickName(), m.x(), m.y(), m.sentAt()));
            } else if ("DRAG_PREVIEW".equals(m.type()) && m.nodeId() != null) {
                dragItems.add(new PresenceBatch.DragItem(
                        m.userId(), m.nodeId(), m.x(), m.y(), m.sentAt()));
            }
        }

        if (cursorItems.isEmpty() && dragItems.isEmpty()) return null;

        return new PresenceBatch(
                "PRESENCE_BATCH",
                teamId,
                graphId,
                System.currentTimeMillis(),
                cursorItems,
                dragItems
        );
    }

    @PreDestroy
    public void stop() {
        flusher.shutdownNow();
    }

    private record RoomIds(Long teamId, Long graphId) {
    }
}
