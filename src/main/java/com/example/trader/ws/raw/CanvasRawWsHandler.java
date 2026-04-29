package com.example.trader.ws.raw;

import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
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

    private final CanvasSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final RealtimePublisher realtimePublisher;
    private final CanvasLockService lockService;

    private static final String TYPE_CURSOR  = "CURSOR";
    private static final String TYPE_DRAG    = "DRAG_PREVIEW";
    private static final String TYPE_CONTROL = "__CONTROL__";

    private static final Set<String> ALLOWED_TYPES =
            Set.of(TYPE_CURSOR, TYPE_DRAG, TYPE_CONTROL);

    private static final String SUB_LOCK_ACQUIRE   = "LOCK_ACQUIRE";
    private static final String SUB_LOCK_RELEASE   = "LOCK_RELEASE";
    private static final String SUB_LOCK_KEEPALIVE = "LOCK_KEEPALIVE";
    private static final String SUB_EDIT_START     = "EDIT_START";
    private static final String SUB_EDIT_END       = "EDIT_END";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            RoomIds room = parseRoomIds(session.getUri());
            String roomKey = registry.roomKey(room.teamId(), room.graphId());

            WebSocketSession safeSession =
                    new ConcurrentWebSocketSessionDecorator(session, 100, 128 * 1024);

            session.getAttributes().put(WsAttrs.ROOM_IDS, room);
            session.getAttributes().put(WsAttrs.ROOM_KEY, roomKey);
            session.getAttributes().put(WsAttrs.SAFE_SESSION, safeSession);

            if (session.getAttributes().get(WsAttrs.USER_ID) == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
                return;
            }

            registry.join(roomKey, safeSession);

        } catch (Exception e) {
            log.error("[RAW] afterConnectionEstablished failed session={} uri={}",
                    session.getId(), session.getUri(), e);
            try {
                session.close();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);
        RoomIds room = (RoomIds) session.getAttributes().get(WsAttrs.ROOM_IDS);
        Long userId = (Long) session.getAttributes().get(WsAttrs.USER_ID);
        String nickName = (String) session.getAttributes().get(WsAttrs.NICKNAME);

        if (roomKey == null || room == null || userId == null) {
            try {
                session.close(CloseStatus.PROTOCOL_ERROR);
            } catch (Exception ignore) {
            }
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
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid type"));
            } catch (Exception ignore) {
            }
            return;
        }

        if (TYPE_DRAG.equals(in.type()) && in.nodeId() == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("nodeId required for drag"));
            } catch (Exception ignore) {
            }
            return;
        }

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
            publishVolatile(out);
        }
    }

    private void handleControlEvent(WebSocketSession session,
                                    String roomKey,
                                    RoomIds room,
                                    RawCursorMessage msg,
                                    Long userId,
                                    String nickName) {
        String subType = msg.subType();

        if (subType == null) {
            publishReliable(msg);
            return;
        }

        switch (subType) {
            case SUB_LOCK_ACQUIRE -> handleLockAcquire(session, room, msg, userId, nickName);
            case SUB_LOCK_RELEASE -> handleLockRelease(room, msg, userId, nickName);
            case SUB_LOCK_KEEPALIVE -> handleLockKeepalive(room, msg, userId);
            case SUB_EDIT_START, SUB_EDIT_END -> {
                // REST 엔드포인트에서 처리하므로 WS 경로에서는 무시
            }
            default -> publishReliable(msg);
        }
    }

    private void handleLockAcquire(WebSocketSession session,
                                   RoomIds room,
                                   RawCursorMessage msg,
                                   Long userId,
                                   String nickName) {
        if (msg.nodeId() == null) return;

        boolean acquired = lockService.tryAcquire(
                room.teamId(),
                room.graphId(),
                msg.nodeId(),
                userId
        );

        if (acquired) {
            publishReliable(
                    controlMsg("LOCK_ACQUIRED", room, userId, nickName, msg.nodeId(), null, null)
            );
        } else {
            Long lockedBy = lockService
                    .getLockHolder(room.teamId(), room.graphId(), msg.nodeId())
                    .orElse(null);

            RawCursorMessage denied =
                    controlMsg("LOCK_DENIED", room, lockedBy, nickName, msg.nodeId(), null, null);

            sendDirect(session, denied);
        }
    }

    private void handleLockRelease(RoomIds room,
                                   RawCursorMessage msg,
                                   Long userId,
                                   String nickName) {
        if (msg.nodeId() == null) return;

        boolean released = lockService.release(
                room.teamId(),
                room.graphId(),
                msg.nodeId(),
                userId
        );

        if (released) {
            publishReliable(
                    controlMsg("LOCK_RELEASED", room, userId, nickName, msg.nodeId(), null, null)
            );
        }
    }

    private void handleLockKeepalive(RoomIds room, RawCursorMessage msg, Long userId) {
        if (msg.nodeId() == null) return;
        lockService.keepAlive(room.teamId(), room.graphId(), msg.nodeId(), userId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);

        log.warn("[RAW] transport error session={} uri={} ex={}",
                session.getId(), session.getUri(), exception.toString());

        if (roomKey != null) {
            registry.leave(roomKey, safeOf(session));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);
        RoomIds room = (RoomIds) session.getAttributes().get(WsAttrs.ROOM_IDS);
        Long userId = (Long) session.getAttributes().get(WsAttrs.USER_ID);
        String nickName = (String) session.getAttributes().get(WsAttrs.NICKNAME);

        if (roomKey != null) {
            registry.leave(roomKey, safeOf(session));
        }

        if (room != null && userId != null) {
            List<Long> releasedNodeIds =
                    lockService.releaseAllByUser(room.teamId(), room.graphId(), userId);

            for (Long nodeId : releasedNodeIds) {
                publishReliable(
                        controlMsg(
                                "LOCK_RELEASED",
                                room,
                                userId,
                                nickName != null ? nickName : "",
                                nodeId,
                                null,
                                null
                        )
                );
            }
        }
    }

    private void publishReliable(RawCursorMessage msg) {
        realtimePublisher.publish(
                RealtimeEnvelope.builder()
                        .type(RealtimeType.RELIABLE)
                        .subType(toRealtimeSubType(msg))
                        .teamId(msg.teamId())
                        .graphId(msg.graphId())
                        .payload(msg)
                        .build()
        );
    }

    private void publishVolatile(RawCursorMessage msg) {
        realtimePublisher.publish(
                RealtimeEnvelope.builder()
                        .type(RealtimeType.VOLATILE)
                        .subType(toRealtimeSubType(msg))
                        .teamId(msg.teamId())
                        .graphId(msg.graphId())
                        .payload(msg)
                        .build()
        );
    }

    private RealtimeSubType toRealtimeSubType(RawCursorMessage msg) {
        if (TYPE_CURSOR.equals(msg.type())) {
            return RealtimeSubType.CURSOR;
        }

        if (TYPE_DRAG.equals(msg.type())) {
            return RealtimeSubType.DRAG_PREVIEW;
        }

        if (TYPE_CONTROL.equals(msg.type()) && msg.subType() != null) {
            return switch (msg.subType()) {
                case "LOCK_ACQUIRE" -> RealtimeSubType.LOCK_ACQUIRE;
                case "LOCK_ACQUIRED" -> RealtimeSubType.LOCK_ACQUIRED;
                case "LOCK_DENIED" -> RealtimeSubType.LOCK_DENIED;
                case "LOCK_RELEASE" -> RealtimeSubType.LOCK_RELEASE;
                case "LOCK_RELEASED" -> RealtimeSubType.LOCK_RELEASED;
                case "LOCK_KEEPALIVE" -> RealtimeSubType.LOCK_KEEPALIVE;
                case "EDIT_START" -> RealtimeSubType.EDIT_START;
                case "EDIT_END" -> RealtimeSubType.EDIT_END;
                default -> RealtimeSubType.CONTROL;
            };
        }

        return RealtimeSubType.CONTROL;
    }

    private void sendDirect(WebSocketSession session, RawCursorMessage msg) {
        WebSocketSession safe = safeOf(session);
        if (!safe.isOpen()) return;

        try {
            safe.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.warn("[RAW] sendDirect failed session={} ex={}", session.getId(), e.toString());
        }
    }

    private WebSocketSession safeOf(WebSocketSession session) {
        Object value = session.getAttributes().get(WsAttrs.SAFE_SESSION);
        return value instanceof WebSocketSession ws ? ws : session;
    }

    private RoomIds parseRoomIds(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri required");
        }

        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        String teamIdStr = params.getFirst("teamId");
        String graphIdStr = params.getFirst("graphId");

        if (teamIdStr == null || graphIdStr == null) {
            throw new IllegalArgumentException(
                    "teamId/graphId query required. ex) /ws/canvas-raw?teamId=1&graphId=2"
            );
        }

        return new RoomIds(Long.valueOf(teamIdStr), Long.valueOf(graphIdStr));
    }

    private RawCursorMessage controlMsg(String subType,
                                        RoomIds room,
                                        Long userId,
                                        String nickName,
                                        Long nodeId,
                                        List<String> fields,
                                        Integer baseVersion) {
        return new RawCursorMessage(
                TYPE_CONTROL,
                subType,
                room.teamId(),
                room.graphId(),
                userId,
                nickName,
                nodeId,
                0,
                0,
                System.currentTimeMillis(),
                fields,
                baseVersion
        );
    }

    public record RoomIds(Long teamId, Long graphId) {
    }
}