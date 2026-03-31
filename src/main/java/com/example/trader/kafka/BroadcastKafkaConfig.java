package com.example.trader.kafka;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import com.example.trader.ws.broadcast.RawCanvasEventBroadcaster;
import com.example.trader.server.ServerStateManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BroadcastKafkaConfig {

    private final ConsumerFactory<String, CanvasEventEnvelope> consumerFactory;
    private final RawCanvasEventBroadcaster rawCanvasEventBroadcaster;
    private final ServerStateManager serverStateManager;

    @Value("${app.kafka.topic.canvas-events:canvas-events}")
    private String topic;

    @Value("${app.kafka.broadcast-group-id:canvas-broadcast-group}")
    private String broadcastGroupId;
    @Value("${app.instance-id}")
    private String instanceId;

    @PostConstruct
    public void debugConfig() {
        log.info("[KAFKA_CONFIG] instanceId={} topic={} broadcastGroupId={}",
                instanceId, topic, broadcastGroupId);
    }

    @Bean(name = "broadcastContainer")
    public ConcurrentMessageListenerContainer<String, CanvasEventEnvelope> broadcastContainer() {
        ContainerProperties properties = new ContainerProperties(topic);
        properties.setGroupId(broadcastGroupId);
        properties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, CanvasEventEnvelope>) record -> {
            CanvasEventEnvelope event = record.value();

            log.info("[KAFKA][BROADCAST_CONSUME] groupId={} entityId={} version={} state={}",
                    event.getGroupId(), event.getEntityId(), event.getVersion(), serverStateManager.snapshot());

            if (serverStateManager.isDraining()) {
                log.info("[KAFKA][BROADCAST_SKIP][DRAIN] entityId={}", event.getEntityId());
                return;
            }

            if (!serverStateManager.isReady()) {
                log.info("[KAFKA][BROADCAST_SKIP][NOT_READY] entityId={}", event.getEntityId());
                return;
            }

            rawCanvasEventBroadcaster.broadcast(event);
        });

        ConcurrentMessageListenerContainer<String, CanvasEventEnvelope> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, properties);

        container.setAutoStartup(false);
        return container;
    }
}