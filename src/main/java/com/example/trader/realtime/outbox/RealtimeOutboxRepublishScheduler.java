package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.example.trader.repository.RealtimeOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class RealtimeOutboxRepublishScheduler {

    private final RealtimeOutboxRepository realtimeOutboxRepository;
    private final KafkaReliablePublisher kafkaReliablePublisher;
    private final KafkaHealthState kafkaHealthState;
    private final ObjectMapper objectMapper;

    @Scheduled(
            fixedDelayString = "${realtime.outbox.republish-delay-ms:5000}"
    )
    public void republishPendingEvents() {
        if (!kafkaHealthState.isAvailable()) {
            return;
        }

        List<RealtimeOutbox> pendingEvents =
                realtimeOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[REALTIME-OUTBOX-REPUBLISH-START] size={}", pendingEvents.size());

        for (RealtimeOutbox outbox : pendingEvents) {
            republishOne(outbox);
        }
    }

    private void republishOne(RealtimeOutbox outbox) {
        try {
            RealtimeEnvelope envelope = objectMapper.readValue(
                    outbox.getEnvelopeJson(),
                    RealtimeEnvelope.class
            );

            kafkaReliablePublisher.publishOrThrow(envelope);

            outbox.markSent();
            realtimeOutboxRepository.save(outbox);

            kafkaHealthState.markUp();

            log.info("[REALTIME-OUTBOX-REPUBLISH-SENT] eventId={}, graphId={}, subType={}",
                    outbox.getEventId(), outbox.getGraphId(), outbox.getSubType());

        } catch (Exception e) {
            kafkaHealthState.markDown();

            outbox.markFailed(e.getMessage());
            outbox.markPendingForRetry();
            realtimeOutboxRepository.save(outbox);

            log.warn("[REALTIME-OUTBOX-REPUBLISH-FAILED] eventId={}, retryCount={}, reason={}",
                    outbox.getEventId(), outbox.getRetryCount(), e.getMessage());
        }
    }
}
