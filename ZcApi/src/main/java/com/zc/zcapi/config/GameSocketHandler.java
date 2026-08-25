package com.zc.zcapi.config;

import com.zc.zcapi.service.GameSocketService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameSocketHandler extends TextWebSocketHandler {
    private final GameSocketService gameSocketService;

    public GameSocketHandler(GameSocketService gameSocketService) {
        this.gameSocketService = gameSocketService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        gameSocketService.add((Long) session.getAttributes().get("gameId"), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long gameId = (Long) session.getAttributes().get("gameId");
        if (gameId != null) gameSocketService.remove(gameId, session);
    }
}
