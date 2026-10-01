package com.example.trader.realtime.propagation;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class MultiCommittedEventPropagator implements CommittedEventPropagator {

    private final ReliableInboundHandler reliableInboundHandler;
    private final ObjectProvider<RedisPubSubPublisher> redisPublisher;
    private final RedisHealthState redisHealthState;

    @Override
    public void propagate(RealtimeEnvelope envelope) {
        propagateLocal(envelope);
        propagateRemote(envelope);
    }

    private void propagateLocal(RealtimeEnvelope envelope) {
        try {
            reliableInboundHandler.handle(envelope);
        } catch (Exception e) {
            log.warn("[REALTIME-LOCAL-AFTER-COMMIT-FAILED] eventId={}, reason={}",
                    envelope.getEventId(), e.getMessage());
        }
    }

    private void propagateRemote(RealtimeEnvelope envelope) {
        if (!redisHealthState.isAvailable()) {
            log.debug("[REALTIME-REDIS-AFTER-COMMIT-SKIPPED] eventId={}, reason=unavailable", envelope.getEventId());
            return;
        }

        RedisPubSubPublisher publisher = redisPublisher.getIfAvailable();
        if (publisher == null) {
            log.debug("[REALTIME-REDIS-AFTER-COMMIT-SKIPPED] eventId={}, reason=unconfigured", envelope.getEventId());
            return;
        }

        try {
            publisher.publish(envelope);
        } catch (Exception e) {
            log.warn("[REALTIME-REDIS-AFTER-COMMIT-FAILED] eventId={}, reason={}",
                    envelope.getEventId(), e.getMessage());
        }
    }

}
