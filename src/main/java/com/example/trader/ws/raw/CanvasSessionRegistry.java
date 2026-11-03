package com.example.trader.ws.raw;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.example.trader.repository.UserTeamRepository;
import com.example.trader.repository.PageRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasSessionRegistry {

    private final UserTeamRepository members;
    private final PageRepository pages;

    public boolean authorized(Long teamId, Long graphId, Object userId) {
        if (teamId == null || graphId == null || teamId <= 0 || graphId <= 0
                || !(userId instanceof Long id) || id <= 0) return false;
        try {
            return members.existsByTeamIdAndUserId(teamId, id)
                    && pages.existsByIdAndDirectoryTeamId(graphId, teamId);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void reject(String roomKey, WebSocketSession session) {
        leave(roomKey, session);
        try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
    }

    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public String roomKey(Long teamId, Long graphId) {
        return "team:%d:graph:%d".formatted(teamId, graphId);
    }

    public void join(String roomKey, WebSocketSession session) {
        rooms.computeIfAbsent(roomKey, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void leave(String roomKey, WebSocketSession session) {
        Set<WebSocketSession> set = rooms.get(roomKey);
        if (set == null) return;

//        boolean removed = set.remove(session);
//        int size = set.size();
//        if (removed) log.info("[RAW] left session={} roomKey={} size={}", session.getId(),roomKey, size);

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
        List<WebSocketSession> current = new ArrayList<>(set);
        try {
            String[] ids = roomKey.split(":");
            if (ids.length != 4 || !ids[0].equals("team") || !ids[2].equals("graph"))
                throw new IllegalArgumentException("Invalid canvas scope");
            Long teamId = Long.valueOf(ids[1]);
            Long graphId = Long.valueOf(ids[3]);
            if (!pages.existsByIdAndDirectoryTeamId(graphId, teamId))
                throw new IllegalStateException("Canvas unavailable");
            // One membership query per room broadcast, not per recipient.
            Set<Long> permitted = new java.util.HashSet<>(members.findAuthorizedUserIds(teamId));
            List<WebSocketSession> allowed = new ArrayList<>();
            for (WebSocketSession session : current) {
                if (permitted.contains(session.getAttributes().get(WsAttrs.USER_ID))) allowed.add(session);
                else reject(roomKey, session);
            }
            return allowed;
        } catch (RuntimeException unavailable) {
            current.forEach(session -> reject(roomKey, session));
            return List.of();
        }
    }

    /** (선택) 디버깅/메트릭용 */
    public int size(String roomKey) {
        Set<WebSocketSession> set = rooms.get(roomKey);
        return set == null ? 0 : set.size();
    }

    public java.util.Set<String> roomKeysSnapshot() {
        return new java.util.HashSet<>(rooms.keySet());
    }
}
