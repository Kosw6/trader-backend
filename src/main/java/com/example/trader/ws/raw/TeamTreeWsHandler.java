package com.example.trader.ws.raw;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class TeamTreeWsHandler extends TextWebSocketHandler {
    private final TeamTreeHub hub;
    private static final String TEAM = "treeTeamId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long teamId;
        try {
            var params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
            if (params.get("teamId") == null || params.get("teamId").size() != 1) {
                throw new IllegalArgumentException("One teamId required");
            }
            teamId = Long.valueOf(params.getFirst("teamId"));
        } catch (RuntimeException e) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!hub.authorized(teamId, session.getAttributes().get(WsAttrs.USER_ID))) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put(TEAM, teamId);
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 100, 128 * 1024);
        session.getAttributes().put(WsAttrs.SAFE_SESSION, safe);
        hub.join(teamId, safe);
        // Sent after subscription, avoiding the connect/refetch race.
        safe.sendMessage(new TextMessage("{\"type\":\"TREE_READY\",\"teamId\":" + teamId + "}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long teamId = (Long) session.getAttributes().get(TEAM);
        if (!hub.authorized(teamId, session.getAttributes().get(WsAttrs.USER_ID))
                || !"PING".equals(message.getPayload())) {
            hub.leave(session);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = (WebSocketSession) session.getAttributes().get(WsAttrs.SAFE_SESSION);
        safe.sendMessage(new TextMessage("PONG"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.leave(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        hub.leave(session);
        session.close(CloseStatus.SERVER_ERROR);
    }
}
