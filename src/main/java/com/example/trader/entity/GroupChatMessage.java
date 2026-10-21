package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "group_chat_message", uniqueConstraints = @UniqueConstraint(
        name = "group_chat_message_retry", columnNames = {"room_id", "sender_id", "client_message_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long roomId;
    private Long senderId;
    @Column(nullable = false, length = 255) private String nickname;
    @Column(nullable = false, length = 2000) private String content;
    @Column(nullable = false, length = 36) private String clientMessageId;
    @Column(nullable = false) private Instant createdAt;

    public static GroupChatMessage create(Long roomId, Long userId, String nickname,
                                          String content, String clientMessageId) {
        GroupChatMessage message = new GroupChatMessage();
        message.roomId = roomId; message.senderId = userId; message.nickname = nickname;
        message.content = content; message.clientMessageId = clientMessageId;
        message.createdAt = Instant.now();
        return message;
    }
}
