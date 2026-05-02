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

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeOutboxService {

    private final RealtimeOutboxRepository realtimeOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void savePending(RealtimeEnvelope envelope) {
        RealtimeEnvelope event = envelope.ensureEventId();

        //비휘발성 아니면 리턴
        if (event.getType() != RealtimeType.RELIABLE) {
            log.debug("[REALTIME-OUTBOX-SKIP] volatile event ignored. eventId={}, type={}, subType={}",
                    event.getEventId(), event.getType(), event.getSubType());
            return;
        }
        //이미 outbox에 중복이 있으면 리턴
        if (realtimeOutboxRepository.existsById(event.getEventId())) {
            log.debug("[REALTIME-OUTBOX-DUPLICATE] already exists. eventId={}", event.getEventId());
            return;
        }

        try {
            RealtimeOutbox outbox = RealtimeOutbox.pending(event, objectMapper);
            realtimeOutboxRepository.save(outbox);

            log.warn("[REALTIME-OUTBOX-SAVED] eventId={}, graphId={}, subType={}",
                    event.getEventId(), event.getGraphId(), event.getSubType());

        } catch (DataIntegrityViolationException e) {
            log.debug("[REALTIME-OUTBOX-RACE-DUPLICATE] eventId={}", event.getEventId());
        }
    }
}
