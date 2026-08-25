package com.zc.zcapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class GameWebSocketConfig implements WebSocketConfigurer {
    private final GameSocketHandler gameSocketHandler;
    private final GameSocketHandshakeInterceptor handshakeInterceptor;

    public GameWebSocketConfig(GameSocketHandler gameSocketHandler,
                               GameSocketHandshakeInterceptor handshakeInterceptor) {
        this.gameSocketHandler = gameSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameSocketHandler, "/ws/games/{gameId}")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
