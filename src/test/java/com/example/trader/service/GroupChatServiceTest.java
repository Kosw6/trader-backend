package com.example.trader.service;

import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.example.trader.realtime.message.*;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupChatServiceTest {
    @Mock GroupChatRoomRepository rooms;
    @Mock GroupChatMessageRepository messages;
    @Mock UserTeamRepository members;
    @Mock TeamRepository teams;
    @Mock UserRepository users;
    @Mock TransactionalReliableEventService events;
    @InjectMocks GroupChatService service;
    final String uuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    GroupChatRoom room;

    @BeforeEach void setup() {
        room = GroupChatRoom.create(1L, "공용", true, 10L);
        ReflectionTestUtils.setField(room, "id", 3L);
        lenient().when(members.existsByTeamIdAndUserId(1L, 10L)).thenReturn(true);
        lenient().when(teams.lockForChat(1L)).thenReturn(Optional.of(Team.builder().id(1L).build()));
        lenient().when(rooms.lockRoom(3L, 1L)).thenReturn(Optional.of(room));
    }

    @Test void commonIsIdempotentAndDoesNotEmitDuplicateCreation() {
        when(rooms.findByTeamIdAndCommonRoomTrue(1L)).thenReturn(Optional.of(room));
        assertThat(service.common(1L,10L).id()).isEqualTo(3L);
        verify(rooms,never()).saveAndFlush(any()); verifyNoInteractions(events);
    }

    @Test void defaultRoomAndEventUseSameTransaction() {
        when(rooms.findByTeamIdAndCommonRoomTrue(1L)).thenReturn(Optional.empty());
        when(rooms.saveAndFlush(any())).thenReturn(room);
        assertThat(service.common(1L,10L).common()).isTrue();
        verify(events).stage(argThat(e -> e.getType()==RealtimeType.RELIABLE
                && e.getSubType()==RealtimeSubType.CHAT_ROOM_CREATED && e.getTeamId()==1L && e.getGraphId()==null));
    }

    @Test void customRoomUsesGroupScopeNotCurrentGraph() {
        when(rooms.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.addRoom(1L,10L,"  실적 분석  ");
        assertThat(result.name()).isEqualTo("실적 분석"); assertThat(result.common()).isFalse();
        verify(events).stage(argThat(e -> e.getGraphId()==null && e.getSubType()==RealtimeSubType.CHAT_ROOM_CREATED));
    }

    @Test void additionalRoomLimitDoesNotDependOnDefaultRoomExistence() {
        when(rooms.countByTeamIdAndCommonRoomFalse(1L)).thenReturn(20L);
        assertThatThrownBy(() -> service.addRoom(1L,10L,"추가")).isInstanceOf(ResponseStatusException.class);
        verify(rooms,never()).saveAndFlush(any()); verifyNoInteractions(events);
    }

    @Test void duplicateRoomNameRejectedBeforeWriting() {
        when(rooms.existsByTeamIdAndNameIgnoreCase(1L,"research")).thenReturn(true);
        assertThatThrownBy(() -> service.addRoom(1L,10L,"research")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(events);
    }

    @ParameterizedTest @ValueSource(strings={"", "   ", "공용", "bad\nroom"})
    void invalidRoomNameRejected(String name) {
        assertThatThrownBy(() -> service.addRoom(1L,10L,name)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(events);
    }

    @Test void nonMembersCannotReadCreateOrSend() {
        assertThatThrownBy(() -> service.list(2L,10L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.common(2L,10L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.addRoom(2L,10L,"실적")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.history(2L,3L,10L,null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.send(2L,3L,10L,"hello",uuid)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(messages,events);
    }

    @Test void roomCannotBeUsedFromAnotherGroup() {
        when(rooms.lockRoom(4L,1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(1L,4L,10L,"hello",uuid)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.history(1L,4L,10L,null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(messages,events);
    }

    @Test void nicknameAndTimeAreServerAuthoredAndSavePrecedesEvent() {
        User user=mock(User.class);when(user.getNickName()).thenReturn("서버 닉네임");
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(messages.saveAndFlush(any())).thenAnswer(call -> {
            GroupChatMessage m=call.getArgument(0); ReflectionTestUtils.setField(m,"id",300L);return m;
        });
        Instant before=Instant.now();
        var result=service.send(1L,3L,10L,"  첫 대화  ",uuid.toUpperCase());
        assertThat(result.nickname()).isEqualTo("서버 닉네임");
        assertThat(result.senderId()).isEqualTo(10L); assertThat(result.content()).isEqualTo("첫 대화");
        assertThat(result.clientMessageId()).isEqualTo(uuid);
        assertThat(result.createdAt()).isBetween(before,Instant.now());
        var order=inOrder(messages,events); order.verify(messages).saveAndFlush(any());
        order.verify(events).stage(argThat(e -> e.getPayload().equals(result) && e.getSubType()==RealtimeSubType.CHAT_MESSAGE_CREATED
                && e.getType()==RealtimeType.RELIABLE && e.getGraphId()==null && e.getTeamId()==1L));
    }

    @Test void retryReturnsSameMessageWithoutSecondSaveOrOutbox() {
        GroupChatMessage original=GroupChatMessage.create(3L,10L,"원래 닉네임","내용",uuid);
        ReflectionTestUtils.setField(original,"id",300L);
        when(messages.findByRoomIdAndSenderIdAndClientMessageId(3L,10L,uuid)).thenReturn(Optional.of(original));
        assertThat(service.send(1L,3L,10L,"내용",uuid).id()).isEqualTo(300L);
        verify(messages,never()).saveAndFlush(any());verifyNoInteractions(events,users);
        assertThatThrownBy(() -> service.send(1L,3L,10L,"다른 내용",uuid)).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest @ValueSource(strings={"", "  ", "\u0000"})
    void invalidContentDoesNotWrite(String content) {
        assertThatThrownBy(() -> service.send(1L,3L,10L,content,uuid)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(messages,events);
    }

    @Test void oversizedContentAndInvalidRetryIdRejected() {
        assertThatThrownBy(() -> service.send(1L,3L,10L,"x".repeat(2001),uuid)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.send(1L,3L,10L,"x","not-a-uuid")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(messages,events);
    }

    @Test void historyReturnsFiftyInAscendingOrderAndBoundedOlderCursor() {
        when(rooms.findByIdAndTeamId(3L,1L)).thenReturn(Optional.of(room));
        List<GroupChatMessage> rows=new ArrayList<>();
        for(long i=100;i>=50;i--){var message=GroupChatMessage.create(3L,10L,"nick","내용",UUID.randomUUID().toString());
            ReflectionTestUtils.setField(message,"id",i);rows.add(message);}
        when(messages.history(eq(3L),isNull(),any())).thenReturn(rows);
        var result=service.history(1L,3L,10L,null);
        assertThat(result.messages()).hasSize(50);
        assertThat(result.messages().get(0).id()).isEqualTo(51L);
        assertThat(result.messages().get(49).id()).isEqualTo(100L);
        assertThat(result.nextBeforeId()).isEqualTo(51L);
    }

    @Test void failedOutboxStageDoesNotPretendSuccess() {
        User user=mock(User.class);when(user.getNickName()).thenReturn("nick");when(users.findById(10L)).thenReturn(Optional.of(user));
        when(messages.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("outbox unavailable")).when(events).stage(any());
        assertThatThrownBy(() -> service.send(1L,3L,10L,"내용",uuid)).isInstanceOf(IllegalStateException.class);
    }
}
