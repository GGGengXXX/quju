package cn.edu.buaa.quju.module.social.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.module.notification.service.NotificationService;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.AiReplySuggestionVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.ForwardMessageReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.GenerateAiReplyReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.MarkReadReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.MessageVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.SendMessageReq;
import cn.edu.buaa.quju.module.social.entity.Message;
import cn.edu.buaa.quju.module.social.mapper.FriendshipMapper;
import cn.edu.buaa.quju.module.social.mapper.MessageMapper;
import cn.edu.buaa.quju.module.social.entity.Friendship;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserAiSettings;
import cn.edu.buaa.quju.module.user.service.UserService;
import cn.edu.buaa.quju.module.social.websocket.ChatWebSocketHandler;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {
    private static final int AI_CONTEXT_LIMIT = 12;

    private final MessageMapper messageMapper;
    private final FriendshipMapper friendshipMapper;
    private final ChatWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ChatAiService chatAiService;
    private final UserService userService;

    public MessageService(MessageMapper messageMapper, FriendshipMapper friendshipMapper,
                          ChatWebSocketHandler wsHandler, ObjectMapper objectMapper,
                          NotificationService notificationService,
                          org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                          ChatAiService chatAiService,
                          UserService userService) {
        this.messageMapper = messageMapper;
        this.friendshipMapper = friendshipMapper;
        this.wsHandler = wsHandler;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
        this.chatAiService = chatAiService;
        this.userService = userService;
    }

    public PageResult<MessageVO> getMessages(long userId, String scope, long peerId, int page, int size) {
        IPage<Message> pg;
        if ("FRIEND".equals(scope)) {
            ensureFriendConversation(userId, peerId);
            pg = messageMapper.selectPage(new Page<>(page, size),
                    Wrappers.<Message>lambdaQuery()
                            .eq(Message::getScope, "FRIEND")
                            .and(w -> w.eq(Message::getSenderId, userId).eq(Message::getReceiverId, peerId)
                                    .or(x -> x.eq(Message::getSenderId, peerId).eq(Message::getReceiverId, userId)))
                            .orderByAsc(Message::getCreatedAt));
        } else if ("TEAM".equals(scope)) {
            requireTeamMember(peerId, userId);
            pg = messageMapper.selectPage(new Page<>(page, size),
                    Wrappers.<Message>lambdaQuery()
                            .eq(Message::getScope, "TEAM")
                            .eq(Message::getTeamId, peerId)
                            .orderByAsc(Message::getCreatedAt));
        } else {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        List<MessageVO> list = pg.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(pg.getTotal(), page, size, list);
    }

    @Transactional
    public MessageVO send(long senderId, SendMessageReq req) {
        if ("FRIEND".equals(req.scope())) {
            ensureFriendConversation(senderId, req.peerId());
        } else if ("TEAM".equals(req.scope())) {
            requireTeamMember(req.peerId(), senderId);
        } else {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        Message msg = new Message();
        msg.setScope(req.scope());
        msg.setSenderId(senderId);
        msg.setContentType(req.contentType());
        msg.setContent(req.content());
        msg.setIsRead(false);
        msg.setIsRecalled(false);
        if ("FRIEND".equals(req.scope())) {
            msg.setReceiverId(req.peerId());
        } else {
            msg.setTeamId(req.peerId());
        }
        messageMapper.insert(msg);
        MessageVO vo = toVO(msg);
        pushMessage(req.scope(), req.peerId(), senderId, vo);
        String preview = req.content() != null && req.content().length() > 20
                ? req.content().substring(0, 20) + "..." : req.content();
        if ("FRIEND".equals(req.scope())) {
            notificationService.send(req.peerId(), "FRIEND_MESSAGE",
                    "收到一条新消息", preview, "USER", senderId);
        } else {
            // 小队群聊：检测@提醒
            List<Long> memberIds = jdbcTemplate.queryForList(
                    "SELECT user_id FROM team_member WHERE team_id = ? AND user_id != ?",
                    Long.class, req.peerId(), senderId);
            String teamName = jdbcTemplate.queryForObject(
                    "SELECT name FROM team WHERE id = ?", String.class, req.peerId());
            String content = req.content() != null ? req.content() : "";
            boolean atAll = content.contains("@所有人");

            for (Long memberId : memberIds) {
                if (atAll) {
                    notificationService.send(memberId, "TEAM_AT",
                            "小队「" + teamName + "」中有人@了所有人", preview, "TEAM", req.peerId());
                } else if (isUserMentioned(content, memberId, req.peerId())) {
                    notificationService.send(memberId, "TEAM_AT",
                            "小队「" + teamName + "」中有人@了你", preview, "TEAM", req.peerId());
                } else {
                    notificationService.send(memberId, "TEAM_MESSAGE",
                            "小队「" + teamName + "」有新消息", preview, "TEAM", req.peerId());
                }
            }
        }
        return vo;
    }

    public AiReplySuggestionVO generateAiReply(long userId, GenerateAiReplyReq req) {
        String normalizedScope = normalizeScope(req.scope());
        List<Message> contextMessages = loadContextMessages(userId, normalizedScope, req.peerId());
        UserAiSettings aiSettings = userService.getAiSettings(userId);
        String prompt = buildConversationPrompt(userId, normalizedScope, req.peerId(), contextMessages);
        String suggestion = chatAiService.generateReply(aiSettings.systemPrompt(), prompt,
                normalizeText(req.draftText()), normalizeText(req.instruction()));
        return new AiReplySuggestionVO(suggestion, contextMessages.size());
    }

    @Transactional
    public void recall(long userId, long messageId) {
        Message msg = messageMapper.selectById(messageId);
        if (msg == null || !msg.getSenderId().equals(userId))
            throw new BizException(ErrorCode.FORBIDDEN);
        if (Boolean.TRUE.equals(msg.getIsRecalled()))
            throw new BizException(ErrorCode.CONFLICT);
        long minutes = ChronoUnit.MINUTES.between(msg.getCreatedAt(), LocalDateTime.now());
        if (minutes >= 2) throw new BizException(ErrorCode.MESSAGE_RECALL_EXPIRED);
        msg.setIsRecalled(true);
        msg.setRecalledAt(LocalDateTime.now());
        messageMapper.updateById(msg);
    }

    @Transactional
    public MessageVO forward(long senderId, long messageId, ForwardMessageReq req) {
        Message original = messageMapper.selectById(messageId);
        if (original == null || Boolean.TRUE.equals(original.getIsRecalled()))
            throw new BizException(ErrorCode.NOT_FOUND);
        Message fwd = new Message();
        fwd.setScope(req.scope());
        fwd.setSenderId(senderId);
        fwd.setContentType(original.getContentType());
        fwd.setContent(original.getContent());
        fwd.setIsRead(false);
        fwd.setIsRecalled(false);
        fwd.setForwardedFromId(messageId);
        if ("FRIEND".equals(req.scope())) {
            fwd.setReceiverId(req.peerId());
        } else {
            fwd.setTeamId(req.peerId());
        }
        messageMapper.insert(fwd);
        MessageVO vo = toVO(fwd);
        pushMessage(req.scope(), req.peerId(), senderId, vo);
        return vo;
    }

    private boolean isUserMentioned(String content, long userId, long teamId) {
        String nickname = jdbcTemplate.query(
                "SELECT u.nickname FROM team_member tm JOIN user u ON u.id = tm.user_id WHERE tm.team_id = ? AND tm.user_id = ?",
                rs -> rs.next() ? rs.getString("nickname") : null, teamId, userId);
        return nickname != null && !nickname.isBlank() && content.contains("@" + nickname);
    }

    private void pushMessage(String scope, long peerId, long senderId, MessageVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            if ("FRIEND".equals(scope)) {
                wsHandler.sendToUser(peerId, json);
            } else {
                wsHandler.sendToTeam(peerId, senderId, json);
            }
        } catch (Exception ignored) {}
    }

    private MessageVO toVO(Message m) {
        Integer readCount = null;
        if ("TEAM".equals(m.getScope())) {
            readCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message_read_receipt WHERE message_id = ?", Integer.class, m.getId());
        }
        return new MessageVO(m.getId(), m.getScope(), m.getSenderId(), m.getReceiverId(),
                m.getTeamId(), m.getContentType(), m.getContent(),
                m.getIsRead(), m.getIsRecalled(), m.getForwardedFromId(), m.getCreatedAt(), readCount);
    }

    @Transactional
    public void markRead(long userId, MarkReadReq req) {
        if ("FRIEND".equals(req.scope())) {
            ensureFriendConversation(userId, req.peerId());
            // 标记对方发给我的消息为已读
            List<Long> updatedIds = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                    .eq(Message::getScope, "FRIEND")
                    .eq(Message::getSenderId, req.peerId())
                    .eq(Message::getReceiverId, userId)
                    .eq(Message::getIsRead, false))
                .stream().map(Message::getId).toList();

            if (!updatedIds.isEmpty()) {
                messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                        .set(Message::getIsRead, true)
                        .in(Message::getId, updatedIds));
                // WebSocket 通知发送方消息已读
                try {
                    String json = objectMapper.writeValueAsString(java.util.Map.of(
                        "type", "READ_RECEIPT", "scope", "FRIEND", "messageIds", updatedIds));
                    wsHandler.sendToUser(req.peerId(), json);
                } catch (Exception ignored) {}
            }
        } else if ("TEAM".equals(req.scope())) {
            requireTeamMember(req.peerId(), userId);
            // 群聊：记录已读回执
            List<Message> unread = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                    .eq(Message::getScope, "TEAM")
                    .eq(Message::getTeamId, req.peerId())
                    .ne(Message::getSenderId, userId)
                    .orderByDesc(Message::getCreatedAt)
                    .last("LIMIT 50"));
            for (Message msg : unread) {
                try {
                    jdbcTemplate.update(
                        "INSERT IGNORE INTO message_read_receipt(message_id, user_id) VALUES (?, ?)",
                        msg.getId(), userId);
                } catch (Exception ignored) {}
            }
            // WebSocket 通知群内所有人有人已读
            try {
                String json = objectMapper.writeValueAsString(java.util.Map.of(
                    "type", "READ_RECEIPT", "scope", "TEAM", "teamId", req.peerId(), "userId", userId));
                wsHandler.sendToTeam(req.peerId(), userId, json);
            } catch (Exception ignored) {}
        }
    }

    private String normalizeScope(String scope) {
        if ("FRIEND".equals(scope) || "TEAM".equals(scope)) return scope;
        throw new BizException(ErrorCode.BAD_REQUEST);
    }

    private void ensureFriendConversation(long userId, long peerId) {
        Long isFriend = friendshipMapper.selectCount(Wrappers.<Friendship>lambdaQuery()
                .eq(Friendship::getOwnerId, userId)
                .eq(Friendship::getFriendId, peerId));
        if (isFriend == null || isFriend == 0) throw new BizException(ErrorCode.NOT_FRIENDS);
    }

    private List<Message> loadContextMessages(long userId, String scope, long peerId) {
        List<Message> records;
        if ("FRIEND".equals(scope)) {
            ensureFriendConversation(userId, peerId);
            records = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                    .eq(Message::getScope, "FRIEND")
                    .and(w -> w.eq(Message::getSenderId, userId).eq(Message::getReceiverId, peerId)
                            .or(x -> x.eq(Message::getSenderId, peerId).eq(Message::getReceiverId, userId)))
                    .orderByDesc(Message::getCreatedAt)
                    .last("LIMIT " + AI_CONTEXT_LIMIT));
        } else {
            requireTeamMember(peerId, userId);
            records = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                    .eq(Message::getScope, "TEAM")
                    .eq(Message::getTeamId, peerId)
                    .orderByDesc(Message::getCreatedAt)
                    .last("LIMIT " + AI_CONTEXT_LIMIT));
        }
        return records.stream()
                .filter(message -> !Boolean.TRUE.equals(message.getIsRecalled()))
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .toList();
    }

    private void requireTeamMember(long teamId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM team_member WHERE team_id = ? AND user_id = ?",
                Integer.class, teamId, userId);
        if (count == null || count == 0) throw new BizException(ErrorCode.FORBIDDEN);
    }

    private String buildConversationPrompt(long currentUserId, String scope, long peerId, List<Message> contextMessages) {
        List<Long> participantIds = new ArrayList<>();
        participantIds.add(currentUserId);
        if ("FRIEND".equals(scope)) {
            participantIds.add(peerId);
        } else {
            contextMessages.stream().map(Message::getSenderId).distinct().forEach(participantIds::add);
        }
        Map<Long, String> nicknames = loadNicknames(participantIds);
        String conversationType = "FRIEND".equals(scope) ? "私聊" : "群聊";
        String currentUserName = nicknames.getOrDefault(currentUserId, "我");
        String peerLabel = "FRIEND".equals(scope)
                ? nicknames.getOrDefault(peerId, "对方")
                : loadTeamName(peerId);

        String transcript = contextMessages.isEmpty()
                ? "暂无历史消息，请根据当前聊天对象给出一个自然开场或跟进回复。"
                : contextMessages.stream()
                        .map(message -> formatTranscriptLine(currentUserId, nicknames, message))
                        .collect(Collectors.joining("\n"));

        return """
                你正在为趣聚聊天场景生成回复草稿。
                会话类型：%s
                当前用户：%s
                会话对象：%s
                要求：
                1. 只输出一条可以直接发送的中文回复，不要解释。
                2. 优先延续最近消息语气，简洁自然，避免编造事实。
                3. 如果上下文里提到时间、地点、报名、活动安排，尽量围绕这些信息回复。
                4. 群聊场景避免误用第一人称代替他人，不要假装系统通知。

                最近消息：
                %s
                """.formatted(conversationType, currentUserName, peerLabel, transcript);
    }

    private String normalizeText(String text) {
        if (text == null) return null;
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<Long, String> loadNicknames(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinctIds.isEmpty()) return Map.of();
        return jdbcTemplate.query(
                "SELECT id, nickname FROM user WHERE id IN (" +
                        distinctIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")",
                rs -> {
                    Map<Long, String> map = new java.util.HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getLong("id"), rs.getString("nickname"));
                    }
                    return map;
                },
                distinctIds.toArray());
    }

    private String loadTeamName(long teamId) {
        String name = jdbcTemplate.query(
                "SELECT name FROM team WHERE id = ?",
                rs -> rs.next() ? rs.getString("name") : null,
                teamId);
        return name == null || name.isBlank() ? "当前群聊" : name;
    }

    private String formatTranscriptLine(long currentUserId, Map<Long, String> nicknames, Message message) {
        String speaker = message.getSenderId() != null && message.getSenderId() == currentUserId
                ? "我"
                : nicknames.getOrDefault(message.getSenderId(), "用户" + message.getSenderId());
        String content;
        if ("IMAGE".equals(message.getContentType())) content = "[图片]";
        else if ("LOCATION".equals(message.getContentType())) content = "[位置]" + message.getContent();
        else content = message.getContent();
        return speaker + "：" + content;
    }
}
