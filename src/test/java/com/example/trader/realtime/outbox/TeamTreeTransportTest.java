package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.infra.redis.pubsub.RedisPubSubPublisher;
import com.example.trader.realtime.message.*;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class TeamTreeTransportTest {
    final ObjectMapper mapper = new ObjectMapper();
    RealtimeEnvelope event() {
        return RealtimeEnvelope.builder().eventId("tree-transport").teamId(7L)
                .type(RealtimeType.RELIABLE).subType(RealtimeSubType.TEAM_TREE_CHANGED)
                .payload(Map.of("scope", "TEAM_TREE")).build();
    }
    @Test void redisUsesTeamChannelAndKafkaUsesTeamPartitionKey() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        new RedisPubSubPublisher(redis, mapper).publish(event());
        verify(redis).convertAndSend(eq("canvas:7:tree"), anyString());
        @SuppressWarnings("unchecked") KafkaTemplate<String, RealtimeEnvelope> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq("canvas-events"), eq("team:7:tree"), any())).thenReturn(CompletableFuture.completedFuture(null));
        var publisher = new KafkaReliablePublisher(kafka);
        ReflectionTestUtils.setField(publisher, "topic", "canvas-events");
        publisher.publishOrThrow(event());
        verify(kafka).send(eq("canvas-events"), eq("team:7:tree"), any());
    }
    @Test void outboxRelayAcceptsOnlyTeamTreeNullGraphNotNodeNullGraphOrTreeGraph() throws Exception {
        for (var e : new RealtimeEnvelope[]{ event(), event().toBuilder().subType(RealtimeSubType.NODE_UPDATED).build(),
                event().toBuilder().graphId(9L).build() }) {
            var claims = mock(RealtimeOutboxClaimService.class);
            var publisher = mock(KafkaReliablePublisher.class);
            var health = mock(KafkaHealthState.class);
            when(health.isAvailable()).thenReturn(true);
            var claim = new ClaimedRealtimeOutbox(e.getEventId(), "token", e.getGraphId(), e.getSubType(), mapper.writeValueAsString(e));
            when(claims.claimByEventId(e.getEventId())).thenReturn(Optional.of(claim));
            new RealtimeOutboxRelay(claims, publisher, health, mapper).publishByEventId(e.getEventId());
            if (e.getSubType() == RealtimeSubType.TEAM_TREE_CHANGED && e.getGraphId() == null) {
                verify(publisher).publishOrThrow(any());
                verify(claims).markSent(e.getEventId(), "token");
            } else {
                verifyNoInteractions(publisher);
                verify(claims).markFailed(eq(e.getEventId()), eq("token"), startsWith("INVALID_EVENT"));
            }
        }
    }
}
