package com.example.trader.ws.raw;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.ws.common.PresenceBatch;
import com.example.trader.ws.common.RoomPresenceCoalescer;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawPresenceBroadcaster {

    private final CanvasSessionRegistry registry;
    private final ObjectMapper objectMapper;

    private final RoomPresenceCoalescer<RawCursorMessage> presenceCoalescer =
            new RoomPresenceCoalescer<>(2000);

    private final RoomPresenceCoalescer<RealtimeEnvelope> reliableCoalescer =
            new RoomPresenceCoalescer<>(2000);

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
        presenceCoalescer.publishLatest(roomKey, key, msg);
    }

    public void publishReliable(String roomKey, RealtimeEnvelope envelope) {
        reliableCoalescer.publishReliable(roomKey, envelope);
    }

    private void flushAllRoomsSafe() {
        try {
            for (String roomKey : registry.roomKeysSnapshot()) {
                flushRoom(roomKey);
            }
        } catch (Exception e) {
            log.warn("[RAW] flushAllRoomsSafe error: {}", e.toString());
        }
    }

    private void flushRoom(String roomKey) {
        var sessions = registry.snapshot(roomKey);
        if (sessions.isEmpty()) return;

        for (RealtimeEnvelope envelope : reliableCoalescer.drainReliable(roomKey)) {
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
}