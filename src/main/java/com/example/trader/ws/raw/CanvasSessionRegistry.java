package com.example.trader.ws.raw;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CanvasSessionRegistry {
    //Raw 세션 저장소

    //동시성 고려
    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public String roomKey(Long teamId, Long graphId) {
        return teamId + ":" + graphId;
    }

    //해당 세션을 방에 저장
    public void join(String roomKey, WebSocketSession session) {
        rooms.computeIfAbsent(roomKey, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    //세션이 끊기면 방에서 삭제
    public void leave(String roomKey, WebSocketSession session) {
        Set<WebSocketSession> set = rooms.get(roomKey);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) rooms.remove(roomKey);
    }

    public Set<WebSocketSession> sessions(String roomKey) {
        return rooms.getOrDefault(roomKey, Set.of()); //룸키가 비어있으면 빈값
    }
}
