package com.example.trader.ops.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class BroadcastConsumerService {

    public static final String BROADCAST_CONTAINER_ID = "canvasBroadcastConsumer";

    private final KafkaListenerEndpointRegistry registry;

    public void startBroadcastContainer() {
        MessageListenerContainer container =
                registry.getListenerContainer(BROADCAST_CONTAINER_ID);

        if (container == null) {
            log.warn("[BROADCAST] container not found id={}", BROADCAST_CONTAINER_ID);
            return;
        }

        if (!container.isRunning()) {
            container.start();
            log.info("[BROADCAST] container started id={}", BROADCAST_CONTAINER_ID);
        }
    }

    public void stopBroadcastContainer() {
        MessageListenerContainer container =
                registry.getListenerContainer(BROADCAST_CONTAINER_ID);

        if (container == null) {
            log.warn("[BROADCAST] container not found id={}", BROADCAST_CONTAINER_ID);
            return;
        }

        if (container.isRunning()) {
            container.stop();
            log.info("[BROADCAST] container stopped id={}", BROADCAST_CONTAINER_ID);
        }
    }

    public boolean isRunning() {
        MessageListenerContainer container =
                registry.getListenerContainer(BROADCAST_CONTAINER_ID);

        return container != null && container.isRunning();
    }
}