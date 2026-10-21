package com.example.trader.dto.chat;

import java.time.Instant;

/** Server-authored identity and time; clientMessageId makes retries idempotent. */
public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String nickname,
        String content,
        Instant createdAt,
        String clientMessageId
) { }
