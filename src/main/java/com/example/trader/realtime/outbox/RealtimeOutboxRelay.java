package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class RealtimeOutboxRelay {

    private final RealtimeOutboxClaimService claimService;
    private final KafkaReliablePublisher kafkaPublisher;
    private final KafkaHealthState kafkaHealthState;
    private final ObjectMapper objectMapper;

    public void publishByEventId(String eventId) {
        if (!kafkaHealthState.isAvailable()) {
            return;
        }

        claimService.claimByEventId(eventId).ifPresent(this::publishClaimed);
    }

    public void publishPendingBatch() {
        for (String eventId : claimService.findPendingEventIds()) {
            publishByEventId(eventId);
        }
    }

    private void publishClaimed(RealtimeOutbox outbox) {
        try {
            RealtimeEnvelope envelope = objectMapper.readValue(
                    outbox.getEnvelopeJson(),
                    RealtimeEnvelope.class
            );
            kafkaPublisher.publishOrThrow(envelope);
            claimService.markSent(outbox.getEventId());
            kafkaHealthState.markUp();
            log.info("[REALTIME-OUTBOX-SENT] eventId={}, graphId={}, subType={}",
                    outbox.getEventId(), outbox.getGraphId(), outbox.getSubType());
        } catch (Exception e) {
            kafkaHealthState.markDown();
            claimService.markPendingForRetry(outbox.getEventId(), e.getMessage());
            log.warn("[REALTIME-OUTBOX-RETRY] eventId={}, reason={}",
                    outbox.getEventId(), e.getMessage());
        }
    }
}
