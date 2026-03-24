package com.example.trader.ws.raw;

import com.example.trader.config.AppProperties;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasRawWsHandler extends TextWebSocketHandler {

    private final CanvasSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final RawPresenceBroadcaster broadcaster;
    private final AppProperties appProperties;

    private static final String TYPE_CURSOR = "CURSOR";
    private static final String TYPE_DRAG = "DRAG_PREVIEW";
    private static final String TYPE_CONTROL = "__CONTROL__";

    private static final Set<String> ALLOWED_TYPES =
            Set.of(TYPE_CURSOR, TYPE_DRAG, TYPE_CONTROL);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            RoomIds room = parseRoomIds(session.getUri());
            String roomKey = registry.roomKey(room.teamId(), room.graphId());

            int sendTimeLimitMs = 100;
            int bufferSizeLimitBytes = 128 * 1024;
            WebSocketSession safeSession =
                    new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, bufferSizeLimitBytes);

            session.getAttributes().put(WsAttrs.ROOM_IDS, room);
            session.getAttributes().put(WsAttrs.ROOM_KEY, roomKey);
            session.getAttributes().put(WsAttrs.SAFE_SESSION, safeSession);

            Long userId = (Long) session.getAttributes().get(WsAttrs.USER_ID);
            String nickName = (String) session.getAttributes().get(WsAttrs.NICKNAME);

            // JWT 인터셉터가 없을 때는 query param fallback
            if (userId == null) {
                UserInfo userInfo = parseUserInfo(session.getUri());
                userId = userInfo.userId();
                nickName = userInfo.nickName();

                session.getAttributes().put(WsAttrs.USER_ID, userId);
                session.getAttributes().put(WsAttrs.NICKNAME, nickName);
            }

            log.info("[RAW][CONNECT] instance={} roomKey={} userId={} session={}",
                    appProperties.getInstanceId(), roomKey, userId, session.getId());

            registry.join(roomKey, safeSession);

        } catch (Exception e) {
            log.error("[RAW] afterConnectionEstablished failed session={} uri={}",
                    session.getId(), session.getUri(), e);
            try { session.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);
        RoomIds room = (RoomIds) session.getAttributes().get(WsAttrs.ROOM_IDS);

        Long userId = (Long) session.getAttributes().get(WsAttrs.USER_ID);
        String nickName = (String) session.getAttributes().get(WsAttrs.NICKNAME);

        if (roomKey == null || room == null || userId == null) {
            // ✅ 프로토콜 에러는 close 해도 됨
            try { session.close(CloseStatus.PROTOCOL_ERROR); } catch (Exception ignore) {}
            return;
        }

        final RawCursorMessage in;
        try {
            in = objectMapper.readValue(message.getPayload(), RawCursorMessage.class);
        } catch (Exception parseEx) {
            // ✅ 파싱 실패로 매번 close하면 k6에서 “연쇄 종료”가 쉽게 발생
            // 그냥 무시(또는 카운터/샘플 로그) 추천
            log.debug("[RAW] invalid json session={} payloadPreview={}",
                    session.getId(),
                    message.getPayload().substring(0, Math.min(200, message.getPayload().length())));
            return;
        }

        // type 검증
        if (in.type() == null || !ALLOWED_TYPES.contains(in.type())) {
            // ✅ 이건 클라가 잘못 보낸 거라 close 해도 됨(406)
            try { session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid type")); } catch (Exception ignore) {}
            return;
        }

        if (TYPE_DRAG.equals(in.type()) && in.nodeId() == null) {
            try { session.close(CloseStatus.NOT_ACCEPTABLE.withReason("nodeId required for drag")); } catch (Exception ignore) {}
            return;
        }

        // server authoritative 정규화
        //TODO:노드 이동시에는 클라에서 해당 센더의 커서대신 노드의 아이디와 x,y좌표를 넘겨야 함
        RawCursorMessage out = new RawCursorMessage(
                in.type(),//커서,드래그,컨트롤 -> 커서와 드래그는 latest로 넘기는데 클라에서 type수신해서 렌더링
                room.teamId(),
                room.graphId(),
                userId,
                nickName,
                in.nodeId(),
                in.x(),
                in.y(),
                in.sentAt()
        );

        if (TYPE_CONTROL.equals(out.type())) {//컨트롤:필수 이벤트
            broadcaster.publishReliable(roomKey, out);     // ✅ DTO 그대로
        } else {//커서,드래그 포함
            String key = makeLatestKey(out.type(), out.userId(), out.nodeId());
            broadcaster.publishLatest(roomKey, key, out);  // ✅ DTO 그대로
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);

        // ✅ 여기서 close() 시도하지 말고 cleanup만
//        log.warn("[RAW] transport error session={} uri={} ex={}",
//                session.getId(), session.getUri(), exception.toString());

        if (roomKey != null) {
            registry.leave(roomKey, safeOf(session));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomKey = (String) session.getAttributes().get(WsAttrs.ROOM_KEY);
        Long userId = (Long) session.getAttributes().get(WsAttrs.USER_ID);
        if (roomKey != null) registry.leave(roomKey, safeOf(session));
        // log.debug("[RAW] closed session={} status={} roomKey={}", session.getId(), status, roomKey);
        log.info("[RAW][CONNECT] instance={} roomKey={} userId={} session={}",
                appProperties.getInstanceId(), roomKey, userId, session.getId());
    }

    private WebSocketSession safeOf(WebSocketSession session) {
        Object v = session.getAttributes().get(WsAttrs.SAFE_SESSION);
        return (v instanceof WebSocketSession ws) ? ws : session;
    }
    //정합성 이벤트 ex)노드 드래그 앤 드롭(좌표수정 후 DB반영), 노드 수정, 엣지 연결등 -> 즉각 UX반영 해야 하기 때문
    private void broadcast(String roomKey, TextMessage payload) {
        // ✅ snapshot으로 안전하게 순회
        for (WebSocketSession s : registry.snapshot(roomKey)) {
            if (s == null) continue;

            if (!s.isOpen()) {
                registry.leave(roomKey, safeOf(s));
                continue;
            }

            try {
                s.sendMessage(payload);
            } catch (Exception sendEx) {
                // ✅ send 실패는 흔히 “상대가 이미 끊김”이므로 close(1011) 하지 말고 제거만
                log.warn("[RAW] send fail roomKey={} session={} ex={}", roomKey, s.getId(), sendEx.toString());
                registry.leave(roomKey, safeOf(s));
            }
        }
    }

    private UserInfo parseUserInfo(URI uri) {
        if (uri == null) throw new IllegalArgumentException("uri required");

        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        String userIdStr = params.getFirst("userId");
        String nickName = params.getFirst("nickName");

        if (userIdStr == null) {
            throw new IllegalArgumentException(
                    "userId query required. ex) /ws/canvas-raw?teamId=1&graphId=2&userId=1001"
            );
        }

        return new UserInfo(Long.valueOf(userIdStr), nickName == null ? "user-" + userIdStr : nickName);
    }

    private record UserInfo(Long userId, String nickName) {}

    private RoomIds parseRoomIds(URI uri) {
        if (uri == null) throw new IllegalArgumentException("uri required");

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

    private String makeLatestKey(String type, Long userId, Long nodeId) {
        // 커서: type:userId
        // 드래그: type:userId:nodeId (노드별 프리뷰가 필요하면)
        if (TYPE_CURSOR.equals(type)) {
            return type + ":" + userId;
        }
        if (TYPE_DRAG.equals(type)) {
            return type + ":" + userId + ":" + (nodeId != null ? nodeId : 0L);
        }
        return type + ":" + userId;
    }
    public record RoomIds(Long teamId, Long graphId) {}

}