package com.example.trader.ws.raw;

import com.example.trader.infra.kafka.KafkaConsumer;
import com.example.trader.infra.redis.pubsub.RedisPubSubListener;
import com.example.trader.realtime.inbound.*;
import com.example.trader.realtime.message.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class GroupChatRealtimeTest {
    @ParameterizedTest @EnumSource(value=RealtimeSubType.class,names={"CHAT_MESSAGE_CREATED","CHAT_ROOM_CREATED"})
    void chatReusesTeamStreamAndDeduplicatesRedisKafkaAcrossTwoServers(RealtimeSubType type) throws Exception {
        var f=new TeamTreeRealtimeTest();
        var a=new TeamTreeHub(f.members,f.mapper);var b=new TeamTreeHub(f.members,f.mapper);
        var sa=f.session("a",10L,"?teamId=1");var sb=f.session("b",11L,"?teamId=1");
        var other=f.session("other",12L,"?teamId=2");
        when(f.members.existsByTeamIdAndUserId(1L,10L)).thenReturn(true);
        when(f.members.existsByTeamIdAndUserId(1L,11L)).thenReturn(true);
        a.join(1L,sa);b.join(1L,sb);b.join(2L,other);
        var ia=f.inbound(a);var ib=f.inbound(b);
        var event=f.event("chat-1").toBuilder().subType(type).payload(Map.of("roomId",3,"content","안녕하세요")).build();
        assertThat(ia.handle(event)).isTrue();
        var listener=new RedisPubSubListener(f.mapper,ib,mock(VolatileInboundHandler.class),mock(TransientInboundHandler.class));
        ReflectionTestUtils.setField(listener,"instanceId","B");
        listener.onMessage(new DefaultMessage("canvas:1:tree".getBytes(),f.mapper.writeValueAsBytes(event)),null);
        new KafkaConsumer(ib).consume(new ConsumerRecord<>("canvas-events",0,0,"team:1:tree",event));
        verify(sa,times(1)).sendMessage(any(TextMessage.class));verify(sb,times(1)).sendMessage(any(TextMessage.class));
        verify(other,never()).sendMessage(any());
        assertThatThrownBy(() -> ib.handle(event.toBuilder().eventId("wrong-scope").graphId(99L).build()))
                .isInstanceOf(IllegalArgumentException.class);
        new KafkaConsumer(ib).consume(new ConsumerRecord<>("canvas-events",0,1,"team:1:tree",event.toBuilder().eventId("chat-2").build()));
        verify(sb,times(2)).sendMessage(any(TextMessage.class));
    }
}
