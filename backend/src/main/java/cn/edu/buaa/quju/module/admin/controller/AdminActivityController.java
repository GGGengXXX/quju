package cn.edu.buaa.quju.module.admin.controller;

import cn.edu.buaa.quju.common.AdminContext;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ActivityListVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ActivityReviewReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.AuditLogVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReasonReq;
import cn.edu.buaa.quju.module.admin.service.AdminActivityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/activities")
public class AdminActivityController {
    private final AdminActivityService activityService;
    public AdminActivityController(AdminActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public R<PageResult<ActivityListVO>> listActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminContext.require();
        return R.ok(activityService.listActivities(status, keyword, page, size));
    }

    @GetMapping("/pending-review")
    public R<PageResult<ActivityListVO>> listPendingReview(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminContext.require();
        return R.ok(activityService.listPendingReview(page, size));
    }

    @GetMapping("/{id}/audit-logs")
    public R<List<AuditLogVO>> auditLogs(@PathVariable Long id) {
        AdminContext.require();
        return R.ok(activityService.getAuditLogs(id));
    }

    @PostMapping("/{id}/review")
    public R<Void> review(@PathVariable Long id, @RequestBody @Valid ActivityReviewReq req) {
        activityService.reviewActivity(AdminContext.require(), id, req);
        return R.<Void>ok(null);
    }

    @PostMapping("/{id}/takedown")
    public R<Void> takedown(@PathVariable Long id, @RequestBody @Valid ReasonReq req) {
        activityService.takedown(AdminContext.require(), id, req);
        return R.<Void>ok(null);
    }

    @PostMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id) {
        activityService.restore(AdminContext.require(), id);
        return R.<Void>ok(null);
    }
}
