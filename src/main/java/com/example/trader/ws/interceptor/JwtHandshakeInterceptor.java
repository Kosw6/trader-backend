package com.example.trader.ws.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.provider.JwtTokenProvider;
import com.example.trader.ws.raw.WsAttrs;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    // 네 실제 쿠키 이름으로 맞추기
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String token = resolveFromCookie(request, ACCESS_TOKEN_COOKIE);

        if (token == null || token.isBlank()) {
            log.warn("WS handshake failed: no token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            DecodedJWT jwt = jwtTokenProvider.validateTokenOrThrow(token);

            Authentication authentication =
                    jwtTokenProvider.getAuthentication(jwt);

            //principal은 UserContext
            UserContext userContext = (UserContext) authentication.getPrincipal();

            Long userId = userContext.getUserDto().getId();
            String nickName = userContext.getUserDto().getNickName();

            //WebSocket 세션에 저장
            attributes.put(WsAttrs.USER_ID, userId);
            attributes.put(WsAttrs.NICKNAME, nickName);


            return true;

        } catch (Exception e) {
            log.warn("WS handshake failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String resolveFromCookie(ServerHttpRequest request, String cookieName) {
        if (!(request instanceof ServletServerHttpRequest servletReq)) return null;

        HttpServletRequest r = servletReq.getServletRequest();
        Cookie[] cookies = r.getCookies();
        if (cookies == null) return null;

        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}