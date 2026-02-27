package com.example.trader.ws.raw;

import com.example.trader.ws.common.RoomPresenceCoalescer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // flush 100ms => 10Hz 전파 (원하면 50ms => 20Hz)
    private final RoomPresenceCoalescer<TextMessage> coalescer =
            new RoomPresenceCoalescer<>(10, 2000);

    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "raw-presence-flusher");
        t.setDaemon(true);
        return t;
    });

    // room별로 주기 flush (여기서는 간단히 전체 룸을 주기적으로 flush)
    // 룸 수가 많아지면 roomKey별 active set으로 최적화 가능
    {
        flusher.scheduleAtFixedRate(this::flushAllRoomsSafe, 0, 33, TimeUnit.MILLISECONDS);
    }

    public void publishLatest(String roomKey, String key, TextMessage msg) {
        coalescer.publishLatest(roomKey, key, msg);
    }

    public void publishReliable(String roomKey, TextMessage msg) {
        coalescer.publishReliable(roomKey, msg);
    }

    private void flushAllRoomsSafe() {
        //-> 센더 이벤트를 하나에 모아서 모든 세션으로 전파하기
        try {
            for (String roomKey : registry.roomKeysSnapshot()) {
                flushRoom(roomKey);
            }
        } catch (Exception e) {
            log.warn("[RAW] flushAllRoomsSafe error: {}", e.toString());
        }
    }

    private void flushRoom(String roomKey) {
        List<WebSocketSession> sessions = registry.snapshot(roomKey);
        if (sessions.isEmpty()) return;

        coalescer.flushRoom(roomKey, (TextMessage msg) -> {
            // msg 하나를 room 내 모든 세션으로 전파

            for (WebSocketSession s : sessions) {
                if (s == null) continue;
                if (!s.isOpen()) {
                    registry.leave(roomKey, s);
                    continue;
                }
                try {
                    s.sendMessage(msg);
                } catch (Exception sendEx) {
                    log.warn("[RAW] send fail roomKey={} session={} ex={}", roomKey, s.getId(), sendEx.toString());
                    registry.leave(roomKey, s);
                }
            }
        });
    }
}
