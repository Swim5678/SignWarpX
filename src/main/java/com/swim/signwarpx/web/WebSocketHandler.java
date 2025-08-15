package com.swim.signwarpx.web;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
@WebSocket
public class WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private final Set<Session> sessions;

    public WebSocketHandler() {
        this.sessions = ConcurrentHashMap.newKeySet();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        sessions.add(session);
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {

        // 處理心跳檢測
        if ("ping".equals(message)) {
            try {
                session.getRemote().sendString("pong");
            } catch (Exception ignored) {
            }
        }
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        sessions.remove(session);
    }

    // 廣播訊息給所有連接的客戶端
    public void broadcast(String message) {
        // 使用複製避免並發修改異常
        Set<Session> sessionsCopy = Set.copyOf(sessions);

        for (Session session : sessionsCopy) {
            try {
                if (session.isOpen()) {
                    session.getRemote().sendString(message);
                } else {
                    // 移除已關閉的會話
                    sessions.remove(session);
                }
            } catch (Exception e) {
                System.err.println("廣播訊息失敗: " + e.getMessage());
                sessions.remove(session); // 移除有問題的會話
            }
        }
    }

    // 獲取當前連接數
    public int getConnectionCount() {
        return sessions.size();
    }

    // 關閉所有連接
    public void closeAllConnections() {
        Set<Session> sessionsCopy = Set.copyOf(sessions);
        for (Session session : sessionsCopy) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                System.err.println("關閉 WebSocket 連接失敗: " + e.getMessage());
            }
        }
        sessions.clear();
    }
}