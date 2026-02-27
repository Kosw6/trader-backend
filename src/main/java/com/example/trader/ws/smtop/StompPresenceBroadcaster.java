package com.example.trader.ws.smtop;

import com.example.trader.ws.common.RoomPresenceCoalescer;
import com.example.trader.ws.common.PresenceBatch;
import com.example.trader.ws.smtop.dto.CursorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Component
public class StompPresenceBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    private final RoomPresenceCoalescer<Object> coalescer =
            new RoomPresenceCoalescer<>( 2000);

    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stomp-presence-flusher");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Boolean> activeRooms = new ConcurrentHashMap<>();

    public StompPresenceBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        flusher.scheduleAtFixedRate(this::flushActiveRoomsSafe, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void markRoomActive(String roomKey) {
        activeRooms.put(roomKey, Boolean.TRUE);
    }

    public void publishLatest(String roomKey, String key, Object msg) {
        markRoomActive(roomKey);
        coalescer.publishLatest(roomKey, key, msg);
    }

    public void publishReliable(String roomKey, Object msg) {
        markRoomActive(roomKey);
        coalescer.publishReliable(roomKey, msg);
    }

    private void flushActiveRoomsSafe() {
        try {
            Set<String> rooms = activeRooms.keySet();
            for (String roomKey : rooms) {
                flushRoom(roomKey);
            }
        } catch (Exception e) {
            log.warn("[STOMP] flushActiveRooms error: {}", e.toString());
        }
    }

    private void flushRoom(String roomKey) {
        String[] parts = roomKey.split(":");
        if (parts.length != 2) return;
        Long teamId = Long.valueOf(parts[0]);
        Long graphId = Long.valueOf(parts[1]);

        String topic = "/topic/teams/" + teamId + "/graphs/" + graphId + "/presence";

        // reliable 먼저(개별)
        for (Object m : coalescer.drainReliable(roomKey)) {
            messagingTemplate.convertAndSend(topic, m);
        }

        // latest는 배치 1건
        var latest = coalescer.snapshotLatest(roomKey);
        if (!latest.isEmpty()) {
            PresenceBatch batch = makeBatch(teamId, graphId, latest);
            if (batch != null) {
                messagingTemplate.convertAndSend(topic, batch);
            }
            messagingTemplate.convertAndSend(topic, batch);
        }
    }

    private PresenceBatch makeBatch(Long teamId, Long graphId, Collection<Object> latest) {
        var items = new ArrayList<PresenceBatch.CursorItem>(latest.size());

        for (Object o : latest) {
            if (o instanceof CursorMessage m && "CURSOR".equals(m.type())) {
                items.add(new PresenceBatch.CursorItem(
                        m.userId(), m.nickName(), m.x(), m.y(), m.sentAt()
                ));
            }
        }

        if (items.isEmpty()) {
            return null;   // 실제 보낼 게 없으면 null
        }

        return new PresenceBatch(
                "PRESENCE_BATCH",
                teamId,
                graphId,
                System.currentTimeMillis(),
                items
        );
    }
}






