package com.example.trader.realtime.propagation;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiCommittedEventPropagatorTest {

    @Test
    void propagatesToLocalWebSocketAndRedis() {
        Fixture fixture = fixture();
        RealtimeEnvelope event = reliableEvent();

        fixture.propagator.propagate(event);

        verify(fixture.reliableInboundHandler).handle(event);
        verify(fixture.redisPublisher).publish(event);
    }

    @Test
    void continuesRedisPropagationWhenLocalPropagationFails() {
        Fixture fixture = fixture();
        RealtimeEnvelope event = reliableEvent();
        doThrow(new IllegalStateException("local failed"))
                .when(fixture.reliableInboundHandler).handle(event);

        fixture.propagator.propagate(event);

        verify(fixture.redisPublisher).publish(event);
    }

    private Fixture fixture() {
        ReliableInboundHandler reliableInboundHandler = mock(ReliableInboundHandler.class);
        RedisPubSubPublisher redisPublisher = mock(RedisPubSubPublisher.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisPubSubPublisher> provider = mock(ObjectProvider.class);
        RedisHealthState healthState = mock(RedisHealthState.class);
        when(provider.getIfAvailable()).thenReturn(redisPublisher);
        when(healthState.isAvailable()).thenReturn(true);

        return new Fixture(
                new MultiCommittedEventPropagator(
                        reliableInboundHandler,
                        provider,
                        healthState
                ),
                reliableInboundHandler,
                redisPublisher
        );
    }

    private RealtimeEnvelope reliableEvent() {
        return RealtimeEnvelope.builder()
                .eventId("event-1")
                .type(RealtimeType.RELIABLE)
                .teamId(1L)
                .graphId(2L)
                .build();
    }

    private record Fixture(
            MultiCommittedEventPropagator propagator,
            ReliableInboundHandler reliableInboundHandler,
            RedisPubSubPublisher redisPublisher
    ) {
    }
}
