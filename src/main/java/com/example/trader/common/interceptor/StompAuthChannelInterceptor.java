package com.example.trader.common.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.security.provider.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    // private final JwtVerifier jwtVerifier;  // 네 JWT 검증기
    //todo
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);

        // CONNECT에서만 JWT 검증
        if (StompCommand.CONNECT.equals(acc.getCommand())) {

            String authHeader = acc.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                // 토큰 없으면 연결 차단
                log.warn("STOMP CONNECT without Authorization header");
                throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
            }

            String token = authHeader.substring(7);

            DecodedJWT jwt = jwtTokenProvider.validateTokenOrThrow(token);

            try {
                Authentication authentication = jwtTokenProvider.getAuthentication(jwt, userDetailsService);

                // ✅ STOMP 세션에 사용자 주입 (핵심)
                acc.setUser(authentication);

                // (선택) SecurityContext도 채워두면 @AuthenticationPrincipal 같은 곳에서 편함
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.info("✅ STOMP CONNECT authenticated: {}", jwt.getSubject());
            } catch (Exception e) {
                log.error("STOMP CONNECT auth failed: {}", e.getMessage());
                throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
            }
        }

        return message;
    }
}

