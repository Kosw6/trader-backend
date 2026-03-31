package com.example.trader.ws;

import com.example.trader.server.ServerStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class MyWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final ServerStateManager serverStateManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.add(session);
        log.info("[WS] connected session={} state={}", session.getId(), serverStateManager.snapshot());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (serverStateManager.isDraining()) {
            log.debug("[DRAIN] ignore inbound ws message session={}", session.getId());
            return;
        }
        log.debug("[WS] inbound session={} payload={}", session.getId(), message.getPayload());
        // 기존 처리
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        log.info("[WS] disconnected session={} code={}", session.getId(), status.getCode());
    }
}