package com.example.trader.ws.kafka;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import com.example.trader.server.ServerStateManager;
import com.example.trader.ws.broadcast.RawCanvasEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
//@Component
@RequiredArgsConstructor
public class CanvasEventConsumer {

    private final ServerStateManager serverStateManager;
    private final RawCanvasEventBroadcaster canvasEventBroadcaster;

    @KafkaListener(
            topics = "canvas-events",
            groupId = "${app.kafka.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(CanvasEventEnvelope event) {
        log.info("[KAFKA][CONSUME] groupId={} entityId={} version={} changedFields={} state={}",
                event.getGroupId(),
                event.getEntityId(),
                event.getVersion(),
                event.getChangedFields(),
                serverStateManager.snapshot());

        if (serverStateManager.isDraining()) {
            log.info("[KAFKA][SKIP_BROADCAST][DRAIN] groupId={} entityId={} version={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion());
            return;
        }

        if (!serverStateManager.isReady()) {
            log.info("[KAFKA][SKIP_BROADCAST][NOT_READY] groupId={} entityId={} version={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion());
            return;
        }

        canvasEventBroadcaster.broadcast(event);
    }
}