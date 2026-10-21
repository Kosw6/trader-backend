package com.example.trader.controller;

import com.example.trader.dto.chat.*;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.GroupChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/teams/{teamId}/chat/rooms")
public class GroupChatController {
    private final GroupChatService chat;
    public record CreateRoom(String name) { }
    public record SendMessage(String content, String clientMessageId) { }
    private Long userId(UserContext user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.getUserDto().getId();
    }
    @GetMapping public List<ChatRoomResponse> rooms(@PathVariable Long teamId, @AuthenticationPrincipal UserContext user) {
        return chat.list(teamId, userId(user));
    }
    @PostMapping("/common") public ChatRoomResponse common(@PathVariable Long teamId, @AuthenticationPrincipal UserContext user) {
        return chat.common(teamId, userId(user));
    }
    @PostMapping public ChatRoomResponse create(@PathVariable Long teamId, @RequestBody CreateRoom body, @AuthenticationPrincipal UserContext user) {
        return chat.addRoom(teamId, userId(user), body.name());
    }
    @GetMapping("/{roomId}/messages") public ChatHistoryResponse history(@PathVariable Long teamId, @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId, @AuthenticationPrincipal UserContext user) {
        return chat.history(teamId, roomId, userId(user), beforeId);
    }
    @PostMapping("/{roomId}/messages") public ChatMessageResponse send(@PathVariable Long teamId, @PathVariable Long roomId,
            @RequestBody SendMessage body, @AuthenticationPrincipal UserContext user) {
        return chat.send(teamId, roomId, userId(user), body.content(), body.clientMessageId());
    }
}
