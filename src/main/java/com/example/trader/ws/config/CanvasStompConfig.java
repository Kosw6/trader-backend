package com.example.trader.ws.config;

import com.example.trader.common.interceptor.StompAuthChannelInterceptor;
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

    private final StompAuthChannelInterceptor stompJwtChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/canvas")
                .setAllowedOriginPatterns("*");
        // 운영에서는 프론트 도메인만 허용

    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라 -> 서버
        registry.setApplicationDestinationPrefixes("/app");

        // 서버 -> 클라
        registry.enableSimpleBroker("/topic", "/queue");

        // 개인 큐 -> 브로드캐스트가 아닌 개인한테 전송(에러,알람,락등 ex)해당 캔버스 권한 없음..Notifycation 전송 등)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompJwtChannelInterceptor);
    }
}

