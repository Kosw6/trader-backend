package com.example.trader.realtime.outbox;

import com.example.trader.infra.kafka.KafkaHealthState;
import com.example.trader.realtime.message.*;
import com.example.trader.realtime.publisher.KafkaReliablePublisher;
import com.example.trader.dto.chat.ChatMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;

class GroupChatOutboxTest {
    @ParameterizedTest @EnumSource(value=RealtimeSubType.class,names={"CHAT_MESSAGE_CREATED","CHAT_ROOM_CREATED"})
    void chatWithNullGraphSurvivesOutboxSerializationAndKafkaAcknowledgement(RealtimeSubType subtype) throws Exception {
        var claims=mock(RealtimeOutboxClaimService.class);var kafka=mock(KafkaReliablePublisher.class);
        var health=mock(KafkaHealthState.class);var mapper=new ObjectMapper().findAndRegisterModules();
        var envelope=RealtimeEnvelope.builder().eventId("chat-test").teamId(1L).graphId(null)
                .type(RealtimeType.RELIABLE).subType(subtype)
                .payload(new ChatMessageResponse(1L,2L,3L,"nickname","content",Instant.now(),"client-test")).build();
        var row=RealtimeOutbox.pending(envelope,mapper);
        var claimed=new ClaimedRealtimeOutbox("chat-test","claim",null,subtype,row.getEnvelopeJson());
        when(health.isAvailable()).thenReturn(true);when(claims.claimByEventId("chat-test")).thenReturn(Optional.of(claimed));
        when(claims.markSent("chat-test","claim")).thenReturn(true);
        new RealtimeOutboxRelay(claims,kafka,health,mapper).publishByEventId("chat-test");
        var order=inOrder(kafka,claims);order.verify(kafka).publishOrThrow(any());order.verify(claims).markSent("chat-test","claim");
        verify(claims,never()).markFailed(anyString(),anyString(),anyString());
    }
}
