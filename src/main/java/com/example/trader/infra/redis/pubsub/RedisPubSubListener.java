package com.example.trader.infra.redis.pubsub;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.inbound.VolatileInboundHandler;
import com.example.trader.realtime.inbound.TransientInboundHandler;
import com.example.trader.realtime.ReliablePropagationDedup;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
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
    private final TransientInboundHandler transientInboundHandler;
    private final ReliablePropagationDedup propagationDedup;
    @Value("${app.instance-id:local}")
    private String instanceId;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            RealtimeEnvelope envelope = objectMapper.readValue(body, RealtimeEnvelope.class);
            log.info("[REDIS-INBOUND] type={}, subType={}, eventId={}",
                    envelope.getType(), envelope.getSubType(), envelope.getEventId());

            if (instanceId.equals(envelope.getOriginInstanceId())) {
                log.debug("[REDIS-INBOUND] origin loopback skipped. eventId={}", envelope.getEventId());
                return;
            }

            if (envelope.getType() == RealtimeType.RELIABLE) {
                if (propagationDedup.hasBeenPropagated(envelope.getEventId())) {
                    log.info("[REDIS-INBOUND] already propagated on this instance, skip. eventId={}",
                            envelope.getEventId());
                    return;
                }
                // 발행 → 수신 구간 lag 측정 (publishedAt이 없는 구버전 메시지는 skip)
                reliableInboundHandler.handle(envelope);
                return;
            }

            if (envelope.getType() == RealtimeType.TRANSIENT) {
                transientInboundHandler.handle(envelope);
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
