package com.example.trader.controller;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.inbound.VolatileInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인스턴스 간 HTTP relay 수신 엔드포인트.
 * HttpVolatilePublisher(발신) → POST /internal/realtime/events → 여기서 수신.
 * gRPC 수신과 동일하게 ReliableInboundHandler / VolatileInboundHandler로 라우팅.
 * /internal/** 경로는 SecurityConfig에서 permitAll 처리됨.
 */
@Slf4j
@RestController
@RequestMapping("/internal/realtime")
@RequiredArgsConstructor
public class InternalRealtimeController {

    private final ReliableInboundHandler reliableInboundHandler;
    private final VolatileInboundHandler volatileInboundHandler;
    private final MeterRegistry meterRegistry;

    @PostMapping("/events")
    public ResponseEntity<Void> receive(@RequestBody RealtimeEnvelope envelope) {
        try {
            log.info("[HTTP-RELAY-INBOUND] type={}, eventId={}", envelope.getType(), envelope.getEventId());

            if (envelope.getType() == RealtimeType.RELIABLE) {
                reliableInboundHandler.handle(envelope);
                meterRegistry.counter(
                        "realtime.relay.inbound",
                        "path", "http",
                        "type", envelope.getType().name(),
                        "subType", envelope.getSubType() == null ? "unknown" : envelope.getSubType().name()
                ).increment();
            } else if (envelope.getType() == RealtimeType.VOLATILE) {
                volatileInboundHandler.handle(envelope);
                meterRegistry.counter(
                        "realtime.relay.inbound",
                        "path", "http",
                        "type", envelope.getType().name(),
                        "subType", envelope.getSubType() == null ? "unknown" : envelope.getSubType().name()
                ).increment();
            } else {
                log.warn("[HTTP-RELAY-INBOUND] unknown type, skipping. type={}", envelope.getType());
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("[HTTP-RELAY-INBOUND] handle failed. eventId={}, reason={}", envelope.getEventId(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
