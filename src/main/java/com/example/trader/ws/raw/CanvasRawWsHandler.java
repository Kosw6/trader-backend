package com.example.trader.ws.raw;

import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class CanvasRawWsHandler extends TextWebSocketHandler {

    private final CanvasSessionRegistry registry;
    private final ObjectMapper objectMapper;

    // 세션별 roomKey 저장용 attribute key
    private static final String ATTR_ROOM = "roomKey";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1) teamId, graphId를 쿼리에서 뽑아서 room join
        RoomIds room = parseRoomIds(session.getUri());
        String roomKey = registry.roomKey(room.teamId(), room.graphId());

        session.getAttributes().put(ATTR_ROOM, roomKey);
        registry.join(roomKey, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomKey = (String) session.getAttributes().get(ATTR_ROOM);
        if (roomKey == null) {
            session.close(CloseStatus.PROTOCOL_ERROR);
            return;
        }

        // 2) JSON 파싱
        RawCursorMessage msg = objectMapper.readValue(message.getPayload(), RawCursorMessage.class);

        // 3) (권장) 경로/room과 payload 불일치 방지: payload의 teamId/graphId는 무시하거나 검증
        // 여기선 roomKey 기준으로만 브로드캐스트하고 payload는 그대로 전달(간단 버전)

        // 4) 브로드캐스트
        broadcast(roomKey, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomKey = (String) session.getAttributes().get(ATTR_ROOM);
        if (roomKey != null) registry.leave(roomKey, session);
    }

    private void broadcast(String roomKey, TextMessage payload) {
        for (WebSocketSession s : registry.sessions(roomKey)) {
            try {
                if (s.isOpen()) s.sendMessage(payload);
            } catch (Exception ignored) {
                // 실패 세션 정리까지 하고 싶으면 여기서 s.close() + registry.leave() 해도 됨
            }
        }
    }

    private RoomIds parseRoomIds(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            throw new IllegalArgumentException("teamId/graphId query required. ex) /ws/canvas-raw?teamId=1&graphId=2");
        }
        Long teamId = null;
        Long graphId = null;

        for (String kv : uri.getQuery().split("&")) {
            String[] parts = kv.split("=");
            if (parts.length != 2) continue;
            if (parts[0].equals("teamId")) teamId = Long.valueOf(parts[1]);
            if (parts[0].equals("graphId")) graphId = Long.valueOf(parts[1]);
        }
        if (teamId == null || graphId == null) {
            throw new IllegalArgumentException("teamId/graphId query required. ex) /ws/canvas-raw?teamId=1&graphId=2");
        }
        return new RoomIds(teamId, graphId);
    }

    private record RoomIds(Long teamId, Long graphId) {}
}
