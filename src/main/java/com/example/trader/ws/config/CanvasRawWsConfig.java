package com.example.trader.ws.config;

import com.example.trader.common.handler.CanvasRawWsHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CanvasRawWsConfig implements WebSocketConfigurer {

    private final CanvasRawWsHandler canvasRawWsHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(canvasRawWsHandler, "/ws/canvas-raw")
                .setAllowedOriginPatterns("*");
    }
}
