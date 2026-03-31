package com.example.trader.ws.broadcast;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import com.example.trader.server.ServerStateManager;
import com.example.trader.ws.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawCanvasEventBroadcaster {

    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final ServerStateManager serverStateManager;

    public void broadcast(CanvasEventEnvelope event) {
        if (serverStateManager.isDraining()) {
            log.info("[BROADCAST][SKIP][DRAIN] groupId={} entityId={} version={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion());
            return;
        }

        if (!serverStateManager.isReady()) {
            log.info("[BROADCAST][SKIP][NOT_READY] groupId={} entityId={} version={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion());
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);

            int success = 0;
            int fail = 0;

            for (WebSocketSession session : sessionRegistry.getAllSessions()) {
                if (!session.isOpen()) continue;

                try {
                    session.sendMessage(new TextMessage(payload));
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[BROADCAST][FAIL] session={}", session.getId(), e);
                }
            }

            log.info("[BROADCAST][OK] groupId={} entityId={} version={} success={} fail={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion(), success, fail);

        } catch (Exception e) {
            log.error("[BROADCAST][SERIALIZE_FAIL] groupId={} entityId={} version={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion(), e);
        }
    }
}