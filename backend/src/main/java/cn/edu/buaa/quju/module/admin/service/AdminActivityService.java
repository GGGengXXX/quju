package cn.edu.buaa.quju.module.admin.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ActivityListVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ActivityReviewReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReasonReq;
import cn.edu.buaa.quju.module.admin.entity.Activity;
import cn.edu.buaa.quju.module.admin.entity.ModerationAction;
import cn.edu.buaa.quju.module.admin.mapper.ActivityMapper;
import cn.edu.buaa.quju.module.admin.mapper.ModerationActionMapper;
import cn.edu.buaa.quju.module.notification.service.NotificationService;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminActivityService {
    private final ActivityMapper activityMapper;
    private final ModerationActionMapper moderationMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public AdminActivityService(ActivityMapper activityMapper, ModerationActionMapper moderationMapper, UserMapper userMapper, NotificationService notificationService) {
        this.activityMapper = activityMapper;
        this.moderationMapper = moderationMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    public PageResult<ActivityListVO> listActivities(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Activity> q = Wrappers.<Activity>lambdaQuery().isNull(Activity::getDeletedAt);
        if (status != null && !status.isBlank()) q.eq(Activity::getStatus, status);
        if (keyword != null && !keyword.isBlank()) q.like(Activity::getName, keyword);
        IPage<Activity> p = activityMapper.selectPage(new Page<>(page, size), q);
        return toPage(p, page, size);
    }

    public PageResult<ActivityListVO> listPendingReview(int page, int size) {
        IPage<Activity> p = activityMapper.selectPage(new Page<>(page, size),
                Wrappers.<Activity>lambdaQuery()
                        .eq(Activity::getStatus, "PENDING_REVIEW")
                        .isNull(Activity::getDeletedAt));
        return toPage(p, page, size);
    }

    @Transactional
    public void reviewActivity(long adminId, long activityId, ActivityReviewReq req) {
        Activity a = requireActivity(activityId);
        if (!"PENDING_REVIEW".equals(a.getStatus())) throw new BizException(ErrorCode.CONFLICT);
        if ("REJECTED".equals(req.result()) && (req.reason() == null || req.reason().isBlank()))
            throw new BizException(ErrorCode.REJECT_REASON_REQUIRED);
        if ("NEEDS_REVISION".equals(req.result()) && (req.reason() == null || req.reason().isBlank()))
            throw new BizException(ErrorCode.REJECT_REASON_REQUIRED);
        switch (req.result()) {
            case "PASSED" -> a.setStatus("PUBLISHED");
            case "REJECTED" -> a.setStatus("REJECTED");
            case "NEEDS_REVISION" -> a.setStatus("REJECTED");
            default -> throw new BizException(ErrorCode.BAD_REQUEST);
        }
        activityMapper.updateById(a);
        String logAction = switch (req.result()) {
            case "PASSED" -> "REVIEW_PASS";
            case "REJECTED" -> "REVIEW_REJECT";
            case "NEEDS_REVISION" -> "REVIEW_REVISE";
            default -> "REVIEW";
        };
        logModeration(adminId, activityId, logAction, req.reason() != null ? req.reason() : "");
        // 通知活动创建者
        String notifyTitle = switch (req.result()) {
            case "PASSED" -> "活动「" + a.getName() + "」审核通过";
            case "REJECTED" -> "活动「" + a.getName() + "」被驳回";
            case "NEEDS_REVISION" -> "活动「" + a.getName() + "」需要修改";
            default -> "活动审核结果";
        };
        notificationService.send(a.getCreatorId(), "ACTIVITY_REVIEW", notifyTitle, req.reason(), "ACTIVITY", activityId);
    }

    @Transactional
    public void takedown(long adminId, long activityId, ReasonReq req) {
        if (req.reason() == null || req.reason().isBlank())
            throw new BizException(ErrorCode.TAKEDOWN_REASON_REQUIRED);
        Activity a = requireActivity(activityId);
        a.setStatus("TAKEN_DOWN");
        activityMapper.updateById(a);
        logModeration(adminId, activityId, "TAKE_DOWN", req.reason());
    }

    @Transactional
    public void restore(long adminId, long activityId) {
        Activity a = requireActivity(activityId);
        if (!"TAKEN_DOWN".equals(a.getStatus())) throw new BizException(ErrorCode.CONFLICT);
        a.setStatus("PUBLISHED");
        activityMapper.updateById(a);
        logModeration(adminId, activityId, "RESTORE", "管理员恢复");
    }

    private Activity requireActivity(long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null || a.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return a;
    }

    private void logModeration(long adminId, long targetId, String action, String reason) {
        ModerationAction m = new ModerationAction();
        m.setAdminId(adminId);
        m.setTargetType("ACTIVITY");
        m.setTargetId(targetId);
        m.setAction(action);
        m.setReason(reason);
        moderationMapper.insert(m);
    }

    private PageResult<ActivityListVO> toPage(IPage<Activity> p, int page, int size) {
        List<Activity> records = p.getRecords();
        List<Long> creatorIds = records.stream().map(Activity::getCreatorId).distinct().toList();
        Map<Long, String> nicknames = Map.of();
        if (!creatorIds.isEmpty()) {
            nicknames = userMapper.selectBatchIds(creatorIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : ""));
        }
        Map<Long, String> finalNicknames = nicknames;
        List<ActivityListVO> list = records.stream()
                .map(a -> new ActivityListVO(a.getId(), a.getCreatorId(),
                        finalNicknames.getOrDefault(a.getCreatorId(), ""),
                        a.getName(), a.getCategory(), a.getStatus(), a.getStartTime(), a.getCreatedAt()))
                .collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), page, size, list);
    }
}
