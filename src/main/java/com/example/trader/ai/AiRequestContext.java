package com.example.trader.ai;

import java.time.Instant;

/** 서비스 자격과 사용자 위임은 별개다. 이 문맥을 일반 사용자 인증으로 승격하지 않는다. */
public record AiRequestContext(boolean indexing, Long userId, String requestId,
                               Instant acceptedAt, Instant deadline) {
    public static final String ATTRIBUTE = AiRequestContext.class.getName();
}
