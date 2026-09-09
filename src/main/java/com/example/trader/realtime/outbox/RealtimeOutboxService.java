package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.repository.RealtimeOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeOutboxService {

    private final RealtimeOutboxRepository realtimeOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void savePending(RealtimeEnvelope envelope) {
        savePendingInternal(envelope);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePendingFallback(RealtimeEnvelope envelope) {
        savePendingInternal(envelope);
    }

    private void savePendingInternal(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();
        if (event.getType() != RealtimeType.RELIABLE || realtimeOutboxRepository.existsById(event.getEventId())) {
            return;
        }
        try {
            realtimeOutboxRepository.save(RealtimeOutbox.pending(event, objectMapper));
        } catch (DataIntegrityViolationException e) {
            log.debug("[REALTIME-OUTBOX-RACE-DUPLICATE] eventId={}", event.getEventId());
        }
    }
}
