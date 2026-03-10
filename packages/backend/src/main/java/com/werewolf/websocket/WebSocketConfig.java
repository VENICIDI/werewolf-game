package com.werewolf.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final RoomWebSocketHandler roomWebSocketHandler;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    
    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler,
                          WebSocketHandshakeInterceptor handshakeInterceptor) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/room/{roomCode}")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
