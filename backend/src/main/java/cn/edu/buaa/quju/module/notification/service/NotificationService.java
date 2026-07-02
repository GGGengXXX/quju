package cn.edu.buaa.quju.module.notification.service;

import cn.edu.buaa.quju.module.notification.entity.Notification;
import cn.edu.buaa.quju.module.notification.mapper.NotificationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationMapper mapper;

    public NotificationService(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    /** 发送一条通知 */
    public void send(long userId, String type, String title, String content, String refType, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(false);
        n.setRefType(refType);
        n.setRefId(refId);
        mapper.insert(n);
    }

    /** 获取用户通知列表（分页） */
    public Page<Notification> list(long userId, Boolean isRead, int page, int size) {
        var qw = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        if (isRead != null) qw.eq(Notification::getIsRead, isRead);
        return mapper.selectPage(new Page<>(page, size), qw);
    }

    /** 未读数量 */
    public long unreadCount(long userId) {
        Long count = mapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
        return count != null ? count : 0;
    }

    /** 标记单条已读 */
    @Transactional
    public void markRead(long userId, long notificationId) {
        Notification n = mapper.selectById(notificationId);
        if (n != null && n.getUserId().equals(userId)) {
            n.setIsRead(true);
            mapper.updateById(n);
        }
    }

    /** 全部标记已读 */
    @Transactional
    public void markAllRead(long userId) {
        Notification update = new Notification();
        update.setIsRead(true);
        mapper.update(update, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
    }
}
