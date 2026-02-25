package com.example.trader.ws.raw;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CanvasSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public String roomKey(Long teamId, Long graphId) {
        return teamId + ":" + graphId;
    }

    public void join(String roomKey, WebSocketSession session) {
        rooms.computeIfAbsent(roomKey, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void leave(String roomKey, WebSocketSession session) {
        Set<WebSocketSession> set = rooms.get(roomKey);
        if (set == null) return;

        set.remove(session);

        // ✅ race 방지: remove할 때 "아직 같은 set"일 때만 제거
        if (set.isEmpty()) {
            rooms.remove(roomKey, set);
        }
    }

    /** ❌ 라이브 Set 노출 금지 */
    @Deprecated
    public Set<WebSocketSession> sessions(String roomKey) {
        return rooms.getOrDefault(roomKey, Set.of());
    }

    /** ✅ broadcast는 이걸 써라: 안정적인 복사본 */
    public List<WebSocketSession> snapshot(String roomKey) {
        Set<WebSocketSession> set = rooms.get(roomKey);
        if (set == null || set.isEmpty()) return List.of();
        return new ArrayList<>(set);
    }

    /** (선택) 디버깅/메트릭용 */
    public int size(String roomKey) {
        Set<WebSocketSession> set = rooms.get(roomKey);
        return set == null ? 0 : set.size();
    }
}