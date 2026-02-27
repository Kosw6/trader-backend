package com.example.trader.ws.smtop;

import com.example.trader.ws.common.RoomPresenceCoalescer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Component
public class StompPresenceBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    private final RoomPresenceCoalescer<Object> coalescer =
            new RoomPresenceCoalescer<>(50, 2000);

    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stomp-presence-flusher");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Boolean> activeRooms = new ConcurrentHashMap<>();

    public StompPresenceBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        flusher.scheduleAtFixedRate(this::flushActiveRoomsSafe, 0, 33, TimeUnit.MILLISECONDS);
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
        // roomKey = teamId:graphId 형태로 가정
        String[] parts = roomKey.split(":");
        if (parts.length != 2) return;
        String teamId = parts[0];
        String graphId = parts[1];

        String topic = "/topic/teams/" + teamId + "/graphs/" + graphId + "/presence";

        coalescer.flushRoom(roomKey, msg -> messagingTemplate.convertAndSend(topic, msg));
    }
}






