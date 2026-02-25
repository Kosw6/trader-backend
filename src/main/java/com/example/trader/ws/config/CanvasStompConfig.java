package com.example.trader.ws.config;

import com.example.trader.ws.interceptor.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@RequiredArgsConstructor
@Configuration
@EnableWebSocketMessageBroker
public class CanvasStompConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    // (선택) Principal까지 세팅하고 싶으면 아래 handshakeHandler도 추가
    // private final JwtPrincipalHandshakeHandler jwtPrincipalHandshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/canvas")
                .addInterceptors(jwtHandshakeInterceptor)   // ✅ 여기서 인증
                // .setHandshakeHandler(jwtPrincipalHandshakeHandler) // (선택) Principal 부여
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    // ✅ CONNECT 인증 삭제하면 inbound channel interceptor는 더 이상 필요 없음
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // registration.interceptors(stompJwtChannelInterceptor);  // ❌ 제거
    }
}

