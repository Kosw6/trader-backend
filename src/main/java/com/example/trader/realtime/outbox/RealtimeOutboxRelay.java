package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import java.util.Objects;
import java.util.Optional;
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
        if (Thread.currentThread().isInterrupted() || !kafkaHealthState.isAvailable()) {
            return;
        }

        Optional<ClaimedRealtimeOutbox> claimed;
        try {
            claimed = claimService.claimByEventId(eventId);
        } catch (DataAccessException | TransactionException e) {
            log.error("[REALTIME-OUTBOX-CLAIM-FAILED] 선점 DB 작업 실패 eventId={}", eventId, e);
            return;
        }
        claimed.ifPresent(this::publishClaimed);
    }

    public void publishPendingBatch() {
        for (String eventId : claimService.findPendingEventIds()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            publishByEventId(eventId);
        }
    }

    private void publishClaimed(ClaimedRealtimeOutbox outbox) {
        RealtimeEnvelope envelope;
        try {
            envelope = objectMapper.readValue(
                    outbox.envelopeJson(),
                    RealtimeEnvelope.class
            );
            validateEnvelope(envelope, outbox);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            recordFailureSafely(outbox, OutboxFailure.INVALID_EVENT, e);
            return;
        }

        try {
            kafkaPublisher.publishOrThrow(envelope);
        } catch (Exception e) {
            OutboxFailure failure = OutboxFailure.classifyPublish(e);
            if (failure == OutboxFailure.INTERRUPTED) {
                Thread.currentThread().interrupt();
            }
            if (failure.unavailable) {
                kafkaHealthState.markDown();
            }
            recordFailureSafely(outbox, failure, e);
            return;
        }

        kafkaHealthState.markUp();
        try {
            if (claimService.markSent(outbox.eventId(), outbox.claimToken())) {
                log.info("[REALTIME-OUTBOX-SENT] eventId={}, graphId={}, subType={}",
                        outbox.eventId(), outbox.graphId(), outbox.subType());
            } else {
                log.warn("[REALTIME-OUTBOX-STALE-RESULT-IGNORED] result=sent eventId={}",
                        outbox.eventId());
            }
        } catch (DataAccessException | TransactionException e) {
            recordFailureSafely(outbox, OutboxFailure.SENT_RECORD_FAILED, e);
        }
    }

    private void validateEnvelope(RealtimeEnvelope envelope, ClaimedRealtimeOutbox outbox) {
        if (envelope == null || envelope.getType() != RealtimeType.RELIABLE
                || envelope.getEventId() == null || envelope.getEventId().isBlank()
                || !Objects.equals(envelope.getEventId(), outbox.eventId())
                || envelope.getSubType() == null || envelope.getSubType() != outbox.subType()
                || envelope.getTeamId() == null || envelope.getGraphId() == null
                || !Objects.equals(envelope.getGraphId(), outbox.graphId())
                || envelope.getPayload() == null) {
            throw new IllegalArgumentException("Outbox 이벤트의 필수 값 또는 저장 식별 정보가 올바르지 않습니다.");
        }
    }

    private void recordFailureSafely(ClaimedRealtimeOutbox outbox, OutboxFailure failure, Exception original) {
        log.warn("[REALTIME-OUTBOX-FAILED] eventId={}, failure={}", outbox.eventId(), failure, original);
        String reason = failure.name() + ": " + original.getMessage();
        try {
            boolean updated = failure.quarantine
                    ? claimService.markFailed(outbox.eventId(), outbox.claimToken(), reason)
                    : claimService.markPendingForRetry(outbox.eventId(), outbox.claimToken(), reason);
            if (!updated) {
                log.warn("[REALTIME-OUTBOX-STALE-RESULT-IGNORED] eventId={}, failure={}", outbox.eventId(), failure);
            }
        } catch (DataAccessException | TransactionException recoveryFailure) {
            log.error("[REALTIME-OUTBOX-RECOVERY-RECORD-FAILED] 복구 DB 기록 실패 eventId={}, originalFailure={}",
                    outbox.eventId(), failure, recoveryFailure);
        }
    }
}
