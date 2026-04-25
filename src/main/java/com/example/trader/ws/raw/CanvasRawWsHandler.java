package com.example.trader.ws.raw;

import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.example.trader.ws.raw.lock.CanvasLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasRawWsHandler extends TextWebSocketHandler {

    private final CanvasSessionRegistry  registry;
    private final ObjectMapper           objectMapper;
    private final RawPresenceBroadcaster broadcaster;
    private final CanvasLockService      lockService;

    // ── 이벤트 타입 상수 ──────────────────────────────────────────────────────

    private static final String TYPE_CURSOR  = "CURSOR";
    private static final String TYPE_DRAG    = "DRAG_PREVIEW";
    private static final String TYPE_CONTROL = "__CONTROL__";

    private static final Set<String> ALLOWED_TYPES = Set.of(TYPE_CURSOR, TYPE_DRAG, TYPE_CONTROL);

    // __CONTROL__ subType 상수
    private static final String SUB_LOCK_ACQUIRE   = "LOCK_ACQUIRE";
    private static final String SUB_LOCK_RELEASE   = "LOCK_RELEASE";
    private static final String SUB_LOCK_KEEPALIVE = "LOCK_KEEPALIVE";
    private static final String SUB_EDIT_START     = "EDIT_START";
    private static final String SUB_EDIT_END       = "EDIT_END";

    // ── 연결 수립 ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            RoomIds room = parseRoomIds(session.getUri());
            String roomKey = registry.roomKey(room.teamId(), room.graphId());

            int sendTimeLimitMs      = 100;
            int bufferSizeLimitBytes = 128 * 1024;
            WebSocketSession safeSession =
                    new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, bufferSizeLimitBytes);

            session.getAttributes().put(WsAttrs.ROOM_IDS,    room);
            session.getAttributes().put(WsAttrs.ROOM_KEY,    roomKey);
            session.getAttributes().put(WsAttrs.SAFE_SESSION, safeSession);

            if (session.getAttributes().get(WsAttrs.USER_ID) == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
                return;
            }
            registry.join(roomKey, safeSession);

        } catch (Exception e) {
            log.error("[RAW] afterConnectionEstablished failed session={} uri={}",
                    session.getId(), session.getUri(), e);
            try { session.close(); } catch (Exception ignore) {}
        }
    }

    // ── 메시지 수신 ───────────────────────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String  roomKey  = (String)  session.getAttributes().get(WsAttrs.ROOM_KEY);
        RoomIds room     = (RoomIds) session.getAttributes().get(WsAttrs.ROOM_IDS);
        Long    userId   = (Long)    session.getAttributes().get(WsAttrs.USER_ID);
        String  nickName = (String)  session.getAttributes().get(WsAttrs.NICKNAME);

        if (roomKey == null || room == null || userId == null) {
            try { session.close(CloseStatus.PROTOCOL_ERROR); } catch (Exception ignore) {}
            return;
        }

        final RawCursorMessage in;
        try {
            in = objectMapper.readValue(message.getPayload(), RawCursorMessage.class);
        } catch (Exception parseEx) {
            log.debug("[RAW] invalid json session={} payloadPreview={}",
                    session.getId(),
                    message.getPayload().substring(0, Math.min(200, message.getPayload().length())));
            return;
        }

        if (in.type() == null || !ALLOWED_TYPES.contains(in.type())) {
            try { session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid type")); } catch (Exception ignore) {}
            return;
        }

        if (TYPE_DRAG.equals(in.type()) && in.nodeId() == null) {
            try { session.close(CloseStatus.NOT_ACCEPTABLE.withReason("nodeId required for drag")); } catch (Exception ignore) {}
            return;
        }

        // server-authoritative 정규화 (userId, nickName 덮어쓰기)
        RawCursorMessage out = new RawCursorMessage(
                in.type(),
                in.subType(),
                room.teamId(),
                room.graphId(),
                userId,
                nickName,
                in.nodeId(),
                in.x(),
                in.y(),
                in.sentAt(),
                in.fields(),
                in.baseVersion()
        );

        if (TYPE_CONTROL.equals(out.type())) {
            handleControlEvent(session, roomKey, room, out, userId, nickName);
        } else {
            // CURSOR / DRAG_PREVIEW → 휘발성 최신값
            String key = makeLatestKey(out.type(), out.userId(), out.nodeId());
            broadcaster.publishLatest(roomKey, key, out);
        }
    }

    // ── __CONTROL__ 분기 ──────────────────────────────────────────────────────

    /**
     * subType 에 따라 락 / 편집 처리.
     * subType 없는 __CONTROL__ 은 기존처럼 reliable 브로드캐스트.
     */
    private void handleControlEvent(WebSocketSession session, String roomKey,
                                    RoomIds room, RawCursorMessage msg,
                                    Long userId, String nickName) {
        String subType = msg.subType();
        if (subType == null) {
            broadcaster.publishReliable(roomKey, msg);
            return;
        }

        switch (subType) {
            case SUB_LOCK_ACQUIRE   -> handleLockAcquire(session, roomKey, room, msg, userId, nickName);
            case SUB_LOCK_RELEASE   -> handleLockRelease(roomKey, room, msg, userId, nickName);
            case SUB_LOCK_KEEPALIVE -> handleLockKeepalive(room, msg, userId);
            // EDIT_START / EDIT_END 는 REST 엔드포인트(TeamNodesController)에서 처리 → WS 경로 무시
            case SUB_EDIT_START, SUB_EDIT_END -> { /* no-op */ }
            default                 -> broadcaster.publishReliable(roomKey, msg); // 알 수 없는 subType → 그냥 릴레이
        }
    }

    // ── Lock handlers ─────────────────────────────────────────────────────────

    private void handleLockAcquire(WebSocketSession session, String roomKey,
                                   RoomIds room, RawCursorMessage msg,
                                   Long userId, String nickName) {
        if (msg.nodeId() == null) return;

        boolean acquired = lockService.tryAcquire(room.teamId(), room.graphId(), msg.nodeId(), userId);

        if (acquired) {
            // 룸 전체에 LOCK_ACQUIRED 브로드캐스트
            broadcaster.publishReliable(roomKey, controlMsg("LOCK_ACQUIRED", room, userId, nickName, msg.nodeId(), null, null));
        } else {
            // 요청자에게만 LOCK_DENIED (lockedBy = 점유자 userId)
            Long lockedBy = lockService.getLockHolder(room.teamId(), room.graphId(), msg.nodeId()).orElse(null);
            RawCursorMessage denied = controlMsg("LOCK_DENIED", room, lockedBy, nickName, msg.nodeId(), null, null);
            sendDirect(session, denied);
        }
    }

    private void handleLockRelease(String roomKey, RoomIds room,
                                   RawCursorMessage msg, Long userId, String nickName) {
        if (msg.nodeId() == null) return;
        boolean released = lockService.release(room.teamId(), room.graphId(), msg.nodeId(), userId);
        if (released) {
            broadcaster.publishReliable(roomKey, controlMsg("LOCK_RELEASED", room, userId, nickName, msg.nodeId(), null, null));
        }
    }

    private void handleLockKeepalive(RoomIds room, RawCursorMessage msg, Long userId) {
        if (msg.nodeId() == null) return;
        lockService.keepAlive(room.teamId(), room.graphId(), msg.nodeId(), userId);
        // keepalive 는 브로드캐스트 불필요
    }

    // ── 연결 종료 / 에러 ──────────────────────────────────────────────────────

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);
        log.warn("[RAW] transport error session={} uri={} ex={}",
                session.getId(), session.getUri(), exception.toString());
        if (roomKey != null) {
            registry.leave(roomKey, safeOf(session));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String  roomKey  = (String)  session.getAttributes().get(WsAttrs.ROOM_KEY);
        RoomIds room     = (RoomIds) session.getAttributes().get(WsAttrs.ROOM_IDS);
        Long    userId   = (Long)    session.getAttributes().get(WsAttrs.USER_ID);
        String  nickName = (String)  session.getAttributes().get(WsAttrs.NICKNAME);

        if (roomKey != null) registry.leave(roomKey, safeOf(session));

        // 연결 끊긴 유저의 모든 락 해제 후 룸 브로드캐스트
        if (room != null && userId != null && roomKey != null) {
            List<Long> releasedNodeIds =
                    lockService.releaseAllByUser(room.teamId(), room.graphId(), userId);

            for (Long nodeId : releasedNodeIds) {
                broadcaster.publishReliable(roomKey,
                        controlMsg("LOCK_RELEASED", room, userId, nickName != null ? nickName : "", nodeId, null, null));
            }
        }
    }

    // ── Broadcast helper (직접 전송, fanout 아님) ────────────────────────────

    /** LOCK_DENIED 처럼 요청자에게만 보낼 때 사용 */
    private void sendDirect(WebSocketSession session, RawCursorMessage msg) {
        WebSocketSession safe = safeOf(session);
        if (!safe.isOpen()) return;
        try {
            safe.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.warn("[RAW] sendDirect failed session={} ex={}", session.getId(), e.toString());
        }
    }

    /** 룸 전체 fanout (정합성 이벤트 즉시 전송용, broadcaster bypass) */
    private void broadcast(String roomKey, TextMessage payload) {
        for (WebSocketSession s : registry.snapshot(roomKey)) {
            if (s == null) continue;
            if (!s.isOpen()) { registry.leave(roomKey, safeOf(s)); continue; }
            try {
                s.sendMessage(payload);
            } catch (Exception sendEx) {
                log.warn("[RAW] send fail roomKey={} session={} ex={}", roomKey, s.getId(), sendEx.toString());
                registry.leave(roomKey, safeOf(s));
            }
        }
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private WebSocketSession safeOf(WebSocketSession session) {
        Object v = session.getAttributes().get(WsAttrs.SAFE_SESSION);
        return (v instanceof WebSocketSession ws) ? ws : session;
    }

    private RoomIds parseRoomIds(URI uri) {
        if (uri == null) throw new IllegalArgumentException("uri required");
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String teamIdStr  = params.getFirst("teamId");
        String graphIdStr = params.getFirst("graphId");
        if (teamIdStr == null || graphIdStr == null) {
            throw new IllegalArgumentException("teamId/graphId query required. ex) /ws/canvas-raw?teamId=1&graphId=2");
        }
        return new RoomIds(Long.valueOf(teamIdStr), Long.valueOf(graphIdStr));
    }

    private String makeLatestKey(String type, Long userId, Long nodeId) {
        if (TYPE_CURSOR.equals(type)) return type + ":" + userId;
        if (TYPE_DRAG.equals(type))   return type + ":" + userId + ":" + (nodeId != null ? nodeId : 0L);
        return type + ":" + userId;
    }

    /** __CONTROL__ 메시지 생성 편의 메서드 */
    private RawCursorMessage controlMsg(String subType, RoomIds room,
                                        Long userId, String nickName,
                                        Long nodeId,
                                        List<String> fields, Integer baseVersion) {
        return new RawCursorMessage(
                TYPE_CONTROL, subType,
                room.teamId(), room.graphId(),
                userId, nickName,
                nodeId,
                0, 0,
                System.currentTimeMillis(),
                fields, baseVersion
        );
    }

    public record RoomIds(Long teamId, Long graphId) {}
}
