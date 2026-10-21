package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "group_chat_room") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupChatRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long teamId;
    @Column(nullable = false, length = 60) private String name;
    @Column(nullable = false) private boolean commonRoom;
    private Long createdBy;
    @Column(nullable = false) private Instant createdAt;

    public static GroupChatRoom create(Long teamId, String name, boolean common, Long creator) {
        GroupChatRoom room = new GroupChatRoom();
        room.teamId = teamId; room.name = name; room.commonRoom = common;
        room.createdBy = creator; room.createdAt = Instant.now();
        return room;
    }
}
