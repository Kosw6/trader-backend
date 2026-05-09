package com.example.trader.infra.redis.pubsub;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.inbound.VolatileInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "realtime.redis.pubsub-enabled",
        havingValue = "true"
)
public class RedisPubSubListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ReliableInboundHandler reliableInboundHandler;
    private final VolatileInboundHandler volatileInboundHandler;
    private final MeterRegistry meterRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            RealtimeEnvelope envelope = objectMapper.readValue(body, RealtimeEnvelope.class);
            meterRegistry.counter(
                    "realtime.relay.inbound",
                    "path", "redis",
                    "type", envelope.getType().name(),
                    "subType", envelope.getSubType() == null ? "unknown" : envelope.getSubType().name()
            ).increment();
            log.info("[REDIS-INBOUND] type={}, subType={}, eventId={}",
                    envelope.getType(), envelope.getSubType(), envelope.getEventId());
            if (envelope.getType() == RealtimeType.RELIABLE) {
                reliableInboundHandler.handle(envelope);
                return;
            }

            if (envelope.getType() == RealtimeType.VOLATILE) {
                volatileInboundHandler.handle(envelope);
                return;
            }

            throw new IllegalArgumentException("Unsupported realtime type: " + envelope.getType());

        } catch (Exception e) {
            throw new IllegalStateException("Redis Pub/Sub message handling failed", e);
        }
    }
}