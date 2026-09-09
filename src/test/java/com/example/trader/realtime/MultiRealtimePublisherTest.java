package com.example.trader.realtime;

import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeLatestKeyResolver;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiRealtimePublisherTest {

    @Test
    void transientIsEnqueuedLocallyAndPublishedToRedisWithOrigin() {
        Fixture fixture = fixture();
        RealtimeEnvelope event = RealtimeEnvelope.builder()
                .type(RealtimeType.TRANSIENT)
                .teamId(1L)
                .graphId(2L)
                .payload("control")
                .build();

        fixture.publisher.publish(event);

        verify(fixture.broadcaster).enqueueOrdered(
                org.mockito.ArgumentMatchers.eq("team:1:graph:2"),
                org.mockito.ArgumentMatchers.argThat(sent ->
                        sent.getEventId() != null && "ws-1".equals(sent.getOriginInstanceId()))
        );
        verify(fixture.redisPublisher).publish(org.mockito.ArgumentMatchers.argThat(sent ->
                sent.getEventId() != null && "ws-1".equals(sent.getOriginInstanceId())));
    }

    @Test
    void volatileStillPublishesToRedisWhenLocalEnqueueFails() {
        Fixture fixture = fixture();
        RawCursorMessage cursor = new RawCursorMessage(
                "CURSOR", null, 1L, 2L, 3L, "user", null,
                10, 20, 100L, null, null, null
        );
        RealtimeEnvelope event = RealtimeEnvelope.builder()
                .type(RealtimeType.VOLATILE)
                .teamId(1L)
                .graphId(2L)
                .payload(cursor)
                .build();
        doThrow(new IllegalStateException("local failed"))
                .when(fixture.broadcaster).publishLatest(any(), any(), any());

        fixture.publisher.publish(event);

        verify(fixture.redisPublisher).publish(any(RealtimeEnvelope.class));
    }

    @Test
    void reliableMustUseTransactionalOutbox() {
        Fixture fixture = fixture();
        RealtimeEnvelope event = RealtimeEnvelope.builder()
                .type(RealtimeType.RELIABLE)
                .teamId(1L)
                .graphId(2L)
                .build();

        assertThatThrownBy(() -> fixture.publisher.publish(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactional outbox");

        verify(fixture.broadcaster, never()).enqueueOrdered(any(), any());
        verify(fixture.redisPublisher, never()).publish(any());
    }

    private Fixture fixture() {
        RawPresenceBroadcaster broadcaster = mock(RawPresenceBroadcaster.class);
        RedisPubSubPublisher redisPublisher = mock(RedisPubSubPublisher.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisPubSubPublisher> provider = mock(ObjectProvider.class);
        RedisHealthState health = mock(RedisHealthState.class);
        when(provider.getIfAvailable()).thenReturn(redisPublisher);
        when(health.isAvailable()).thenReturn(true);

        MultiRealtimePublisher publisher = new MultiRealtimePublisher(
                broadcaster,
                new ObjectMapper(),
                new RealtimeLatestKeyResolver(),
                provider,
                health
        );
        ReflectionTestUtils.setField(publisher, "instanceId", "ws-1");
        return new Fixture(publisher, broadcaster, redisPublisher);
    }

    private record Fixture(
            MultiRealtimePublisher publisher,
            RawPresenceBroadcaster broadcaster,
            RedisPubSubPublisher redisPublisher
    ) {
    }
}
