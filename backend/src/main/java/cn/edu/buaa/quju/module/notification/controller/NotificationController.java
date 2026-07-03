package cn.edu.buaa.quju.module.notification.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.notification.entity.Notification;
import cn.edu.buaa.quju.module.notification.service.NotificationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = UserContext.require();
        Page<Notification> pg = service.list(userId, isRead, page, size);
        return R.ok(Map.of(
                "total", pg.getTotal(),
                "page", page,
                "size", size,
                "list", pg.getRecords()
        ));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(service.unreadCount(UserContext.require()));
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        service.markRead(UserContext.require(), id);
        return R.ok(null);
    }

    @PostMapping("/read-all")
    public R<Void> markAllRead() {
        service.markAllRead(UserContext.require());
        return R.ok(null);
    }
}
