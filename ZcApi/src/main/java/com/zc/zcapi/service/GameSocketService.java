package com.zc.zcapi.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameSocketService {
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByGame = new ConcurrentHashMap<>();

    public void add(long gameId, WebSocketSession session) {
        sessionsByGame.computeIfAbsent(gameId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(long gameId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByGame.get(gameId);
        if (sessions == null) return;
        sessions.remove(session);
        if (sessions.isEmpty()) sessionsByGame.remove(gameId, sessions);
    }

    public void disconnectUserAfterCommit(long gameId, long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    disconnectUser(gameId, userId);
                }
            });
            return;
        }
        disconnectUser(gameId, userId);
    }

    private void disconnectUser(long gameId, long userId) {
        Set<WebSocketSession> sessions = sessionsByGame.get(gameId);
        if (sessions == null) return;
        for (WebSocketSession session : sessions) {
            if (!Long.valueOf(userId).equals(session.getAttributes().get("userId"))) continue;
            session.getAttributes().put("disabled", true);
            try {
                session.close();
            } catch (IOException ignored) {
            }
            remove(gameId, session);
        }
    }

    public void broadcastGameUpdatedAfterCommit(long gameId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastGameUpdated(gameId);
                }
            });
            return;
        }
        broadcastGameUpdated(gameId);
    }

    public void broadcastGameUpdated(long gameId) {
        Set<WebSocketSession> sessions = sessionsByGame.get(gameId);
        if (sessions == null) return;
        TextMessage message = new TextMessage("{\"type\":\"GAME_UPDATED\"}");
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException exception) {
                remove(gameId, session);
            }
        }
    }
}
