package com.example.trader.ws.raw;

public interface JwtPrincipalExtractor {
    /**
     * token을 검증하고 userId/nickName을 반환
     * 유효하지 않으면 예외 던지기(Handshake에서 차단)
     */
    WsPrincipal extract(String token);

    record WsPrincipal(Long userId, String nickName) {}
}
