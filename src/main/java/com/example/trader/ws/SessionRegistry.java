package com.example.trader.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//연결된 웹소켓 세션을 관리하는 레지스트리
@Slf4j
@Component
public class SessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void add(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void remove(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public Set<WebSocketSession> getAllSessions() {
        return Set.copyOf(sessions.values());
    }

    public void closeAll() {
        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                log.warn("[SESSION] close fail session={}", session.getId(), e);
            }
        }
    }
}