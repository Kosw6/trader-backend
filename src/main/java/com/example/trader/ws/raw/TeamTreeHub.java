package com.example.trader.ws.raw;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.repository.UserTeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Separate team scope: never joins a graph room or accepts graph writes. */
@Component
@RequiredArgsConstructor
public class TeamTreeHub {
    private final UserTeamRepository members;
    private final ObjectMapper mapper;
    private final Map<String, Subscription> sessions = new ConcurrentHashMap<>();

    public boolean authorized(Long teamId, Object userId) {
        return teamId != null && teamId > 0 && userId instanceof Long id
                && members.existsByTeamIdAndUserId(teamId, id);
    }

    public void join(Long teamId, WebSocketSession session) {
        sessions.put(session.getId(), new Subscription(teamId, session));
    }

    public void leave(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public void publish(RealtimeEnvelope event) {
        final TextMessage message;
        try {
            message = new TextMessage(mapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize tree event", e);
        }
        for (Subscription subscription : sessions.values()) {
            if (!subscription.teamId().equals(event.getTeamId())) continue;
            WebSocketSession session = subscription.session();
            // Membership may have been revoked since the handshake. Fail closed.
            if (!authorized(subscription.teamId(), session.getAttributes().get(WsAttrs.USER_ID))) {
                close(session);
                continue;
            }
            try {
                if (session.isOpen()) session.sendMessage(message);
                else leave(session);
            } catch (Exception ignored) {
                close(session); // reconnect performs an authoritative reload
            }
        }
    }

    private void close(WebSocketSession session) {
        leave(session);
        try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
    }

    private record Subscription(Long teamId, WebSocketSession session) { }
}
