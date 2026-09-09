package com.example.trader.realtime;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeLatestKeyResolver;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class MultiRealtimePublisher implements RealtimePublisher {

    private final RawPresenceBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final RealtimeLatestKeyResolver latestKeyResolver;
    private final ObjectProvider<RedisPubSubPublisher> redisPublisher;
    private final RedisHealthState redisHealthState;

    @Value("${app.instance-id:local}")
    private String instanceId;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        if (envelope.getType() == com.example.trader.realtime.message.RealtimeType.RELIABLE) {
            throw new IllegalArgumentException("RELIABLE events must be staged through the transactional outbox");
        }

        RealtimeEnvelope event = envelope.ensureEventId().ensureOriginInstanceId(instanceId);
        propagateLocal(event);
        propagateRedis(event);
    }

    private void propagateLocal(RealtimeEnvelope event) {
        try {
            String roomKey = roomKey(event);
            switch (event.getType()) {
                case TRANSIENT -> broadcaster.enqueueOrdered(roomKey, event);
                case VOLATILE -> {
                    RawCursorMessage message = objectMapper.convertValue(event.getPayload(), RawCursorMessage.class);
                    broadcaster.publishLatest(roomKey, latestKeyResolver.resolve(message), message);
                }
                default -> throw new IllegalArgumentException("Unsupported live realtime type: " + event.getType());
            }
        } catch (Exception e) {
            log.warn("[REALTIME-LOCAL-PUBLISH-FAILED] eventId={}, type={}, reason={}",
                    event.getEventId(), event.getType(), e.getMessage());
        }
    }

    private void propagateRedis(RealtimeEnvelope event) {
        if (!redisHealthState.isAvailable()) {
            log.debug("[REALTIME-REDIS-PUBLISH-SKIPPED] eventId={}, reason=unavailable", event.getEventId());
            return;
        }
        RedisPubSubPublisher publisher = redisPublisher.getIfAvailable();
        if (publisher == null) {
            log.debug("[REALTIME-REDIS-PUBLISH-SKIPPED] eventId={}, reason=unconfigured", event.getEventId());
            return;
        }
        try {
            publisher.publish(event);
        } catch (Exception e) {
            log.warn("[REALTIME-REDIS-PUBLISH-FAILED] eventId={}, type={}, reason={}",
                    event.getEventId(), event.getType(), e.getMessage());
        }
    }

    private String roomKey(RealtimeEnvelope event) {
        return "team:%d:graph:%d".formatted(event.getTeamId(), event.getGraphId());
    }
}
