package com.example.trader.recovery;

import com.example.trader.edit.dto.CanvasEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastConsumerService {

    private final ConcurrentMessageListenerContainer<String, CanvasEventEnvelope> broadcastContainer;

    public void startBroadcastContainer() {
        log.info("[KAFKA_MODE][START_REQUEST] beforeRunning={}", broadcastContainer.isRunning());

        if (!broadcastContainer.isRunning()) {
            broadcastContainer.start();
            log.info("[KAFKA_MODE] broadcast container started");
        }
    }

    public void stopBroadcastContainer() {
        boolean beforeRunning = broadcastContainer.isRunning();
        log.info("[KAFKA_MODE][STOP_REQUEST] beforeRunning={}", beforeRunning);

        if (broadcastContainer.isRunning()) {
            broadcastContainer.stop();
            log.info("[KAFKA_MODE] broadcast container stopped");
        }
    }

    public boolean isRunning() {
        return broadcastContainer.isRunning();
    }
}