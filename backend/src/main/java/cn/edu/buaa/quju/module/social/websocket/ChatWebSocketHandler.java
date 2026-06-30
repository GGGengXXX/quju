package cn.edu.buaa.quju.module.social.websocket;

import cn.edu.buaa.quju.common.JwtUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket 实时推送：客户端连接时带 ?token=xxx 鉴权。
 * 在线用户 session 保存在内存 map；收到 REST 发消息后推送到目标用户。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    // userId -> sessions（同一用户可多端在线）
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<WebSocketSession>> userSessions
            = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;

    public ChatWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = resolveUserId(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        session.getAttributes().put("userId", userId);
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("WS 连接：userId={} sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            CopyOnWriteArrayList<WebSocketSession> list = userSessions.get(userId);
            if (list != null) {
                list.remove(session);
                if (list.isEmpty()) userSessions.remove(userId);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端可发心跳 "ping"，其余不处理（发消息走 REST）
    }

    /** 向指定用户推送消息（单聊）。 */
    public void sendToUser(long userId, String json) {
        List<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return;
        TextMessage msg = new TextMessage(json);
        sessions.forEach(s -> sendQuietly(s, msg));
    }

    /** 向指定小队所有在线成员推送（群聊，排除发送者）。 */
    public void sendToTeam(long teamId, long excludeSenderId, String json) {
        // 查找该队的在线成员
        // team 模块实现前：向所有在线用户广播（排除发送者）
        TextMessage msg = new TextMessage(json);
        userSessions.forEach((uid, sessions) -> {
            if (uid != excludeSenderId) sessions.forEach(s -> sendQuietly(s, msg));
        });
    }

    private Long resolveUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return jwtUtil.parse(param.substring(6));
            }
        }
        return null;
    }

    private void sendQuietly(WebSocketSession s, TextMessage msg) {
        try {
            if (s.isOpen()) s.sendMessage(msg);
        } catch (Exception e) {
            log.warn("WS 推送失败：{}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession s) {
        try { s.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {}
    }
}
