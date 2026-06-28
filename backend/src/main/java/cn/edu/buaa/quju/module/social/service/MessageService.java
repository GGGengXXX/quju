package cn.edu.buaa.quju.module.social.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.PageResult;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.ForwardMessageReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.MarkReadReq;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.MessageVO;
import cn.edu.buaa.quju.module.social.dto.SocialDtos.SendMessageReq;
import cn.edu.buaa.quju.module.social.entity.Message;
import cn.edu.buaa.quju.module.social.mapper.FriendshipMapper;
import cn.edu.buaa.quju.module.social.mapper.MessageMapper;
import cn.edu.buaa.quju.module.social.entity.Friendship;
import cn.edu.buaa.quju.module.social.websocket.ChatWebSocketHandler;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MessageService {
    private final MessageMapper messageMapper;
    private final FriendshipMapper friendshipMapper;
    private final ChatWebSocketHandler wsHandler;
    private final ObjectMapper objectMapper;

    public MessageService(MessageMapper messageMapper, FriendshipMapper friendshipMapper,
                          ChatWebSocketHandler wsHandler, ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.friendshipMapper = friendshipMapper;
        this.wsHandler = wsHandler;
        this.objectMapper = objectMapper;
    }

    public PageResult<MessageVO> getMessages(long userId, String scope, long peerId, int page, int size) {
        IPage<Message> pg;
        if ("FRIEND".equals(scope)) {
            pg = messageMapper.selectPage(new Page<>(page, size),
                    Wrappers.<Message>lambdaQuery()
                            .eq(Message::getScope, "FRIEND")
                            .and(w -> w.eq(Message::getSenderId, userId).eq(Message::getReceiverId, peerId)
                                    .or(x -> x.eq(Message::getSenderId, peerId).eq(Message::getReceiverId, userId)))
                            .orderByAsc(Message::getCreatedAt));
        } else {
            pg = messageMapper.selectPage(new Page<>(page, size),
                    Wrappers.<Message>lambdaQuery()
                            .eq(Message::getScope, "TEAM")
                            .eq(Message::getTeamId, peerId)
                            .orderByAsc(Message::getCreatedAt));
        }
        List<MessageVO> list = pg.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(pg.getTotal(), page, size, list);
    }

    @Transactional
    public MessageVO send(long senderId, SendMessageReq req) {
        if ("FRIEND".equals(req.scope())) {
            Long isFriend = friendshipMapper.selectCount(Wrappers.<Friendship>lambdaQuery()
                    .eq(Friendship::getOwnerId, senderId).eq(Friendship::getFriendId, req.peerId()));
            if (isFriend == null || isFriend == 0) throw new BizException(ErrorCode.NOT_FRIENDS);
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
        // 实时推送给接收方
        pushMessage(req.scope(), req.peerId(), senderId, vo);
        return vo;
    }

    @Transactional
    public void markRead(long userId, MarkReadReq req) {
        if ("FRIEND".equals(req.scope())) {
            messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                    .set(Message::getIsRead, true)
                    .eq(Message::getScope, "FRIEND")
                    .eq(Message::getSenderId, req.peerId())
                    .eq(Message::getReceiverId, userId)
                    .eq(Message::getIsRead, false));
        }
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
        return new MessageVO(m.getId(), m.getScope(), m.getSenderId(), m.getReceiverId(),
                m.getTeamId(), m.getContentType(), m.getContent(),
                m.getIsRead(), m.getIsRecalled(), m.getForwardedFromId(), m.getCreatedAt());
    }
}
