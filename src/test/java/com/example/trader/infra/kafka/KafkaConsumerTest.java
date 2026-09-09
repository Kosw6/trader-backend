package com.example.trader.infra.kafka;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerTest {

    @Test
    void delegatesReliableEventRegardlessOfOriginInstance() {
        ReliableInboundHandler handler = mock(ReliableInboundHandler.class);
        when(handler.handle(any())).thenReturn(true);
        KafkaConsumer consumer = new KafkaConsumer(handler);
        RealtimeEnvelope envelope = event(RealtimeType.RELIABLE, "ws-1");

        consumer.consume(record(envelope));

        verify(handler).handle(envelope);
    }

    @Test
    void ignoresNonReliableEvent() {
        ReliableInboundHandler handler = mock(ReliableInboundHandler.class);
        KafkaConsumer consumer = new KafkaConsumer(handler);
        RealtimeEnvelope envelope = event(RealtimeType.TRANSIENT, "ws-1");

        consumer.consume(record(envelope));

        verify(handler, never()).handle(envelope);
    }

    private ConsumerRecord<String, RealtimeEnvelope> record(RealtimeEnvelope envelope) {
        return new ConsumerRecord<>("canvas-events", 0, 0L, envelope.getEventId(), envelope);
    }

    private RealtimeEnvelope event(RealtimeType type, String originInstanceId) {
        return RealtimeEnvelope.builder()
                .eventId("event-1")
                .originInstanceId(originInstanceId)
                .type(type)
                .teamId(1L)
                .graphId(2L)
                .build();
    }
}
