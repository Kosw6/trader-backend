package com.example.trader.ws.raw;
import com.example.trader.ws.config.CanvasRawWsConfig;
import com.example.trader.ws.interceptor.JwtHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import java.util.List;
import static org.mockito.Mockito.*;
class CanvasOrigin630Test {
 @Test void rawCanvasUsesSameExplicitOriginAllowlistAsTeamTree() {
  var raw=mock(CanvasRawWsHandler.class);var tree=mock(TeamTreeWsHandler.class);var jwt=mock(JwtHandshakeInterceptor.class);
  var config=new CanvasRawWsConfig(raw,tree,jwt);ReflectionTestUtils.setField(config,"allowedOrigins",List.of("https://revestlab.com"));
  var registry=mock(WebSocketHandlerRegistry.class);var a=mock(WebSocketHandlerRegistration.class,RETURNS_SELF);var b=mock(WebSocketHandlerRegistration.class,RETURNS_SELF);
  when(registry.addHandler(tree,"/ws/team-tree")).thenReturn(a);when(registry.addHandler(raw,"/ws/canvas-raw")).thenReturn(b);
  config.registerWebSocketHandlers(registry);verify(a).setAllowedOriginPatterns("https://revestlab.com");verify(b).setAllowedOriginPatterns("https://revestlab.com");verify(b,never()).setAllowedOriginPatterns("*");
 }
}
