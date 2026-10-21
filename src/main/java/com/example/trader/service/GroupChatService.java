package com.example.trader.service;

import com.example.trader.dto.chat.*;
import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.example.trader.realtime.message.*;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupChatService {
    private final GroupChatRoomRepository rooms;
    private final GroupChatMessageRepository messages;
    private final UserTeamRepository members;
    private final TeamRepository teams;
    private final UserRepository users;
    private final TransactionalReliableEventService events;

    private void authorize(Long teamId, Long userId) {
        if (teamId == null || teamId <= 0 || userId == null || !members.existsByTeamIdAndUserId(teamId, userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "그룹 구성원만 채팅을 사용할 수 있어요.");
    }

    private void lockTeam(Long teamId, Long userId) {
        authorize(teamId, userId);
        teams.lockForChat(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(teamId, userId);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> list(Long teamId, Long userId) {
        authorize(teamId, userId);
        return rooms.findByTeamIdOrderByCommonRoomDescIdAsc(teamId).stream().map(GroupChatService::roomDto).toList();
    }

    @Transactional(timeout = 10)
    public ChatRoomResponse common(Long teamId, Long userId) {
        lockTeam(teamId, userId);
        return rooms.findByTeamIdAndCommonRoomTrue(teamId).map(GroupChatService::roomDto)
                .orElseGet(() -> create(teamId, userId, "공용", true));
    }

    @Transactional(timeout = 10)
    public ChatRoomResponse addRoom(Long teamId, Long userId, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isBlank() || name.length() > 60 || name.equals("공용") || name.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방 이름은 1~60자이며 공용은 예약된 이름입니다.");
        lockTeam(teamId, userId);
        if (rooms.countByTeamIdAndCommonRoomFalse(teamId) >= 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "추가 채팅방은 그룹당 20개까지 만들 수 있어요.");
        if (rooms.existsByTeamIdAndNameIgnoreCase(teamId, name))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 이름의 채팅방이 있어요.");
        return create(teamId, userId, name, false);
    }

    private ChatRoomResponse create(Long teamId, Long userId, String name, boolean common) {
        ChatRoomResponse result = roomDto(rooms.saveAndFlush(GroupChatRoom.create(teamId, name, common, userId)));
        stage(teamId, RealtimeSubType.CHAT_ROOM_CREATED, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse history(Long teamId, Long roomId, Long userId, Long beforeId) {
        authorize(teamId, userId);
        rooms.findByIdAndTeamId(roomId, teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (beforeId != null && beforeId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        List<GroupChatMessage> descending = messages.history(roomId, beforeId, PageRequest.of(0, 51));
        List<ChatMessageResponse> result = new ArrayList<>(descending.stream().limit(50).map(GroupChatService::messageDto).toList());
        Collections.reverse(result);
        Long next = descending.size() > 50 ? result.get(0).id() : null;
        return new ChatHistoryResponse(result, next);
    }

    @Transactional(timeout = 10)
    public ChatMessageResponse send(Long teamId, Long roomId, Long userId, String text, String requestId) {
        authorize(teamId, userId);
        String content = text == null ? "" : text.strip();
        if (content.isBlank() || content.length() > 2000 || content.indexOf(0) >= 0
                || requestId == null || !requestId.matches("[0-9a-fA-F-]{36}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 1~2000자이며 전송 식별자가 필요해요.");
        String retryId;
        try { retryId = UUID.fromString(requestId).toString(); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        // Serializes same-room retries and commit order: ID cursor cannot skip an uncommitted lower ID.
        rooms.lockRoom(roomId, teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        authorize(teamId, userId);
        var existing = messages.findByRoomIdAndSenderIdAndClientMessageId(roomId, userId, retryId);
        if (existing.isPresent()) {
            if (!existing.get().getContent().equals(content))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 전송 식별자로 다른 내용을 보낼 수 없어요.");
            return messageDto(existing.get());
        }
        User sender = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ChatMessageResponse result = messageDto(messages.saveAndFlush(
                GroupChatMessage.create(roomId, userId, sender.getNickName(), content, retryId)));
        stage(teamId, RealtimeSubType.CHAT_MESSAGE_CREATED, result);
        return result;
    }

    private void stage(Long teamId, RealtimeSubType type, Object payload) {
        events.stage(RealtimeEnvelope.builder().teamId(teamId).graphId(null)
                .type(RealtimeType.RELIABLE).subType(type).payload(payload).build());
    }
    private static ChatRoomResponse roomDto(GroupChatRoom r) {
        return new ChatRoomResponse(r.getId(), r.getName(), r.isCommonRoom(), r.getCreatedBy());
    }
    private static ChatMessageResponse messageDto(GroupChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getRoomId(), m.getSenderId(), m.getNickname(), m.getContent(), m.getCreatedAt(), m.getClientMessageId());
    }
}
