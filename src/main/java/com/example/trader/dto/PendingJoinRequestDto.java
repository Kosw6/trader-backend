package com.example.trader.dto;

import com.example.trader.entity.JoinRequest;

import java.time.LocalDateTime;

public record PendingJoinRequestDto(
        Long requestId,
        Long requesterId,
        String requesterNickName,
        LocalDateTime createdAt
) {
    public static PendingJoinRequestDto of(JoinRequest jr) {
        return new PendingJoinRequestDto(
                jr.getId(),
                jr.getRequester().getId(),
                jr.getRequester().getNickName(),
                jr.getCreatedAt()
        );
    }
}
