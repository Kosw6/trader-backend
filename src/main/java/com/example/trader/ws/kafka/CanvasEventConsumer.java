package com.example.trader.ws.kafka;

import com.example.trader.edit.DraftRedisStore;
import com.example.trader.edit.dto.CanvasEventEnvelope;
import com.example.trader.edit.dto.DraftEditState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.role", havingValue = "ws")
public class CanvasEventConsumer {

    private final DraftRedisStore draftRedisStore;

    @PostConstruct
    public void init() {
        log.info("[KAFKA][INIT] CanvasEventConsumer initialized");
    }

    @KafkaListener(
            topics = "canvas-events",
//            groupId = "ws-shard-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(CanvasEventEnvelope event) {
        log.info("[KAFKA][RECEIVED] groupId={}, entityId={}, version={}, changedFields={}",
                event.getGroupId(), event.getEntityId(), event.getVersion(), event.getChangedFields());
        Set<String> editingUsers = draftRedisStore.findEditingUsers(event.getGroupId(), event.getEntityId());
        if (editingUsers == null || editingUsers.isEmpty()) {
            log.info("[KAFKA][SKIP] no editing users. groupId={}, entityId={}",
                    event.getGroupId(), event.getEntityId());
            return;
        }

        for (String userIdStr : editingUsers) {
            Long userId = Long.valueOf(userIdStr);
            DraftEditState draft = draftRedisStore.find(event.getGroupId(), event.getEntityId(), userId);

            if (draft == null) {
                continue;
            }

            if (event.getVersion() > draft.getBaseVersion()) {
                draft.getServerChangedFieldsAfterEdit().addAll(event.getChangedFields());
                draftRedisStore.save(draft);
            }
        }

        log.info("[KAFKA][UPDATED_DRAFT_META] groupId={}, entityId={}",
                event.getGroupId(), event.getEntityId());
    }
}