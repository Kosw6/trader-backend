package com.example.trader.infra.redis.pubsub;

import com.example.trader.realtime.ReliablePropagationDedup;
import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.inbound.TransientInboundHandler;
import com.example.trader.realtime.inbound.VolatileInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class RedisPubSubListenerTest {

    @Test
    void skipsMessagePublishedByCurrentInstanceWithoutDedupCacheLookup() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ReliableInboundHandler reliable = mock(ReliableInboundHandler.class);
        VolatileInboundHandler volatileHandler = mock(VolatileInboundHandler.class);
        TransientInboundHandler transientHandler = mock(TransientInboundHandler.class);
        ReliablePropagationDedup dedup = mock(ReliablePropagationDedup.class);
        RedisPubSubListener listener = new RedisPubSubListener(
                objectMapper,
                reliable,
                volatileHandler,
                transientHandler,
                dedup
        );
        ReflectionTestUtils.setField(listener, "instanceId", "ws-1");
        RealtimeEnvelope envelope = RealtimeEnvelope.builder()
                .eventId("event-1")
                .originInstanceId("ws-1")
                .type(RealtimeType.RELIABLE)
                .teamId(1L)
                .graphId(2L)
                .build();
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(objectMapper.writeValueAsBytes(envelope));

        listener.onMessage(message, new byte[0]);

        verify(reliable, never()).handle(any());
        verify(dedup, never()).hasBeenPropagated("event-1");
    }
}
