package com.example.trader.ws.config;


import com.example.trader.ws.interceptor.JwtHandshakeInterceptor;
import com.example.trader.ws.raw.CanvasRawWsHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CanvasRawWsConfig implements WebSocketConfigurer {
    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:http://localhost:*}")
    private java.util.List<String> allowedOrigins;

    private final CanvasRawWsHandler canvasRawWsHandler;
    private final com.example.trader.ws.raw.TeamTreeWsHandler teamTreeWsHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(teamTreeWsHandler, "/ws/team-tree")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
        registry.addHandler(canvasRawWsHandler, "/ws/canvas-raw")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }


}
