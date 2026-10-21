package com.example.trader.ws.raw;

import com.example.trader.infra.kafka.KafkaConsumer;
import com.example.trader.infra.redis.pubsub.RedisPubSubListener;
import com.example.trader.realtime.LocalReliableEventDeduplicator;
import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.inbound.TransientInboundHandler;
import com.example.trader.realtime.inbound.VolatileInboundHandler;
import com.example.trader.realtime.message.*;
import com.example.trader.repository.UserTeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.*;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamTreeRealtimeTest {
    final ObjectMapper mapper = new ObjectMapper();
    final UserTeamRepository members = mock(UserTeamRepository.class);

    WebSocketSession session(String id, Long userId, String query) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.getUri()).thenReturn(URI.create("ws://localhost/ws/team-tree" + query));
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) attrs.put(WsAttrs.USER_ID, userId);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    RealtimeEnvelope event(String id) {
        return RealtimeEnvelope.builder().eventId(id).teamId(1L).type(RealtimeType.RELIABLE)
                .subType(RealtimeSubType.TEAM_TREE_CHANGED).payload(Map.of("scope", "TEAM_TREE")).build();
    }

    ReliableInboundHandler inbound(TeamTreeHub hub) {
        return new ReliableInboundHandler(mock(RawPresenceBroadcaster.class),
                new LocalReliableEventDeduplicator(100, Duration.ofMinutes(10)), hub);
    }

    @Test void twoServerRedisDeliveryAndKafkaRecoveryDeduplicatePerServerAndIsolateTeams() throws Exception {
        TeamTreeHub a = new TeamTreeHub(members, mapper), b = new TeamTreeHub(members, mapper);
        var sa = session("a", 10L, "?teamId=1");
        var sb = session("b", 11L, "?teamId=1");
        var other = session("other", 12L, "?teamId=2");
        when(members.existsByTeamIdAndUserId(1L, 10L)).thenReturn(true);
        when(members.existsByTeamIdAndUserId(1L, 11L)).thenReturn(true);
        a.join(1L, sa); b.join(1L, sb); b.join(2L, other);
        var ia = inbound(a); var ib = inbound(b);
        var event = event("tree-1");
        assertThat(ia.handle(event)).isTrue();
        var listener = new RedisPubSubListener(mapper, ib,
                mock(VolatileInboundHandler.class), mock(TransientInboundHandler.class));
        ReflectionTestUtils.setField(listener, "instanceId", "B");
        listener.onMessage(new DefaultMessage("canvas:1:tree".getBytes(), mapper.writeValueAsBytes(event)), null);
        // Redis duplicate and Kafka fallback must not double-deliver to B.
        listener.onMessage(new DefaultMessage("canvas:1:tree".getBytes(), mapper.writeValueAsBytes(event)), null);
        new KafkaConsumer(ib).consume(new ConsumerRecord<>("canvas-events", 0, 0, "team:1:tree", event));
        verify(sa, times(1)).sendMessage(any(TextMessage.class));
        verify(sb, times(1)).sendMessage(any(TextMessage.class));
        verify(other, never()).sendMessage(any());
        // Next event missed by Redis is recovered through the same Kafka path.
        new KafkaConsumer(ib).consume(new ConsumerRecord<>("canvas-events", 0, 1, "team:1:tree", event("tree-2")));
        verify(sb, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test void rejectsNonMemberAnonymousMalformedAndDuplicateTeamIds() throws Exception {
        var handler = new TeamTreeWsHandler(new TeamTreeHub(members, mapper));
        for (var s : new WebSocketSession[]{session("no", 10L, "?teamId=1"),
                session("anon", null, "?teamId=1"), session("bad", 10L, "?teamId=x"),
                session("dup", 10L, "?teamId=1&teamId=2"), session("zero", 10L, "?teamId=0")}) {
            handler.afterConnectionEstablished(s);
            verify(s).close(any(CloseStatus.class));
            verify(s, never()).sendMessage(any());
        }
    }

    @Test void readyAfterSubscribeHeartbeatAndNoClientWrites() throws Exception {
        var hub = new TeamTreeHub(members, mapper);
        var handler = new TeamTreeWsHandler(hub);
        var s = session("member", 10L, "?teamId=1");
        when(members.existsByTeamIdAndUserId(1L, 10L)).thenReturn(true);
        handler.afterConnectionEstablished(s);
        verify(s).sendMessage(argThat(m -> m instanceof TextMessage t && t.getPayload().contains("TREE_READY")));
        handler.handleTextMessage(s, new TextMessage("PING"));
        verify(s).sendMessage(argThat(m -> m instanceof TextMessage t && t.getPayload().equals("PONG")));
        handler.handleTextMessage(s, new TextMessage("{\"type\":\"TEAM_TREE_CHANGED\",\"teamId\":2}"));
        verify(s).close(CloseStatus.POLICY_VIOLATION);
        hub.publish(event("closed"));
        verify(s, times(2)).sendMessage(any());
    }

    @Test void revokedMemberCannotReceiveAndDoesNotBlockRemainingMembers() throws Exception {
        var hub = new TeamTreeHub(members, mapper);
        var revoked = session("revoked", 10L, "?teamId=1");
        var allowed = session("allowed", 11L, "?teamId=1");
        when(members.existsByTeamIdAndUserId(1L, 11L)).thenReturn(true);
        hub.join(1L, revoked); hub.join(1L, allowed);
        hub.publish(event("revoke"));
        verify(revoked).close(CloseStatus.POLICY_VIOLATION);
        verify(revoked, never()).sendMessage(any());
        verify(allowed).sendMessage(any());
    }

    @Test void invalidScopeRejectedBeforeDedupClaim() {
        var handler = inbound(new TeamTreeHub(members, mapper));
        assertThatThrownBy(() -> handler.handle(event("invalid").toBuilder().graphId(5L).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(handler.handle(event("invalid"))).isTrue();
    }

    @Test void failedDeliveryRemovesSocketAndClosedSessionDoesNotLeak() throws Exception {
        var hub = new TeamTreeHub(members, mapper);
        var s = session("failed", 10L, "?teamId=1");
        when(members.existsByTeamIdAndUserId(1L, 10L)).thenReturn(true);
        doThrow(new java.io.IOException()).when(s).sendMessage(any());
        hub.join(1L, s);
        hub.publish(event("one")); hub.publish(event("two"));
        verify(s, times(1)).sendMessage(any());
        verify(s).close(CloseStatus.POLICY_VIOLATION);
    }
}
