package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.kafka.enabled", havingValue = "true")
public class RealtimeOutboxRepublishScheduler {

    private final RealtimeOutboxRelay relay;
    private final RealtimeOutboxClaimService claimService;
    private final KafkaHealthState kafkaHealthState;

    @Scheduled(
            fixedDelayString = "${realtime.outbox.republish-delay-ms:5000}"
    )
    public void republishPendingEvents() {
        int recovered = claimService.recoverExpiredPublishing(LocalDateTime.now());
        if (recovered > 0) {
            log.warn("[REALTIME-OUTBOX-STALE-RECOVERED] count={}", recovered);
        }
        if (!kafkaHealthState.isAvailable()) {
            return;
        }
        relay.publishPendingBatch();
    }
}
