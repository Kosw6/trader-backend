package com.example.trader.common.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class CanvasRawWsBaseHandler extends TextWebSocketHandler {

    // roomKey -> sessions
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomKey = roomKey(session);
        rooms.computeIfAbsent(roomKey, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("RAW connected room={} session={}", roomKey, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomKey = roomKey(session);
        var set = rooms.get(roomKey);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) rooms.remove(roomKey);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomKey = roomKey(session);

        // 그냥 같은 room에 브로드캐스트 (정합성 없는 이벤트에 최적)
        var set = rooms.get(roomKey);
        if (set == null) return;

        for (WebSocketSession s : set) {
            if (s.isOpen()) s.sendMessage(message);
        }
    }

    private String roomKey(WebSocketSession session) {
        // 예: /ws/canvas-raw?teamId=1&graphId=2
        URI uri = session.getUri();
        String query = (uri == null) ? "" : uri.getQuery();
        Map<String, String> q = parseQuery(query);
        return "t:" + q.getOrDefault("teamId", "0") + "|g:" + q.getOrDefault("graphId", "0");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) map.put(part.substring(0, i), part.substring(i + 1));
        }
        return map;
    }
}
