package com.zc.zcapi.config;

import com.zc.zcapi.common.CurrentUser;
import com.zc.zcapi.service.AuthService;
import com.zc.zcapi.service.GameService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class GameSocketHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(GameSocketHandshakeInterceptor.class);
    private final AuthService authService;
    private final GameService gameService;

    public GameSocketHandshakeInterceptor(AuthService authService, GameService gameService) {
        this.authService = authService;
        this.gameService = gameService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String[] parts = httpRequest.getRequestURI().split("/");
        if (parts.length == 0) return false;
        try {
            long gameId = Long.parseLong(parts[parts.length - 1]);
            String authorization = httpRequest.getHeader("Authorization");
            String token = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7) : queryValue(httpRequest.getQueryString(), "token");
            CurrentUser user = authService.authenticate(token);
            if (!gameService.canAccessGame(user.id(), gameId)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put("gameId", gameId);
            attributes.put("userId", user.id());
            return true;
        } catch (RuntimeException exception) {
            log.warn("WebSocket 握手失败：{}", exception.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String queryValue(String query, String name) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
