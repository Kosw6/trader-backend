package com.example.trader.drain;

import com.example.trader.drain.dto.DrainRequest;
import com.example.trader.drain.dto.ReconnectRequiredMessage;
import com.example.trader.server.ServerStateManager;
import com.example.trader.ws.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrainService {

    private final ServerStateManager serverStateManager;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;

    public synchronized void startDrain(DrainRequest request) {
        if (serverStateManager.isDraining()) {
            log.info("[DRAIN] already draining state={}", serverStateManager.snapshot());
            return;
        }

        serverStateManager.markDraining(true);
        serverStateManager.markReady(false);

        log.info("[DRAIN] started reason={} grace={}ms",
                request.getReason(), request.getGracePeriodMillis());

        broadcastReconnectRequired(request);
        scheduleForceClose(request.getGracePeriodMillis());
    }

    private void broadcastReconnectRequired(DrainRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(
                    ReconnectRequiredMessage.builder()
                            .type("RECONNECT_REQUIRED")
                            .reason(request.getReason())
                            .gracePeriodMillis(request.getGracePeriodMillis())
                            .build()
            );

            int count = 0;
            for (WebSocketSession session : sessionRegistry.getAllSessions()) {
                if (!session.isOpen()) continue;
                try {
                    session.sendMessage(new TextMessage(payload));
                    count++;
                } catch (Exception e) {
                    log.warn("[DRAIN] reconnect notify failed session={}", session.getId(), e);
                }
            }

            log.info("[DRAIN] reconnect notice broadcast done sessionCount={}", count);
        } catch (Exception e) {
            log.error("[DRAIN] reconnect payload serialize failed", e);
        }
    }

    private void scheduleForceClose(long gracePeriodMillis) {
        taskScheduler.schedule(() -> {
            int remain = sessionRegistry.getAllSessions().size();
            log.info("[DRAIN] force closing remainingSessions={}", remain);
            sessionRegistry.closeAll();
        }, Instant.now().plusMillis(gracePeriodMillis));
    }
}