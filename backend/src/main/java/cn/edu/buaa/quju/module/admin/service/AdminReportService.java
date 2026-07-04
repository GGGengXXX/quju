package cn.edu.buaa.quju.module.admin.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReasonReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReportHandleReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReportVO;
import cn.edu.buaa.quju.module.report.entity.Report;
import cn.edu.buaa.quju.module.report.mapper.ReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminReportService {
    private final ReportMapper reportMapper;
    private final AdminTeamService adminTeamService;
    private final AdminActivityService adminActivityService;

    public AdminReportService(ReportMapper reportMapper,
                              AdminTeamService adminTeamService,
                              AdminActivityService adminActivityService) {
        this.reportMapper = reportMapper;
        this.adminTeamService = adminTeamService;
        this.adminActivityService = adminActivityService;
    }

    public PageResult<ReportVO> listReports(String status, int page, int size) {
        LambdaQueryWrapper<Report> q = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) q.eq(Report::getStatus, status);
        q.orderByDesc(Report::getCreatedAt);
        IPage<Report> p = reportMapper.selectPage(new Page<>(page, size), q);
        List<ReportVO> list = p.getRecords().stream()
                .map(r -> new ReportVO(r.getId(), r.getReporterId(), r.getTargetType(),
                        r.getTargetId(), r.getReason(), r.getDetail(), r.getStatus(), r.getCreatedAt()))
                .collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), page, size, list);
    }

    @Transactional
    public void handleReport(long adminId, long reportId, ReportHandleReq req) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!"PENDING".equals(report.getStatus())) throw new BizException(ErrorCode.CONFLICT, "举报已处理");

        String action = req.action() == null ? "" : req.action().trim().toUpperCase();
        switch (action) {
            case "DISMISS" -> report.setStatus("DISMISSED");
            case "RESOLVE" -> report.setStatus("HANDLED");
            case "TAKEDOWN" -> {
                if (req.reason() == null || req.reason().isBlank())
                    throw new BizException(ErrorCode.TAKEDOWN_REASON_REQUIRED);
                takeActionOnTarget(adminId, report, req.reason());
                report.setStatus("HANDLED");
            }
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "处理动作非法");
        }
        report.setHandlerAdminId(adminId);
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private void takeActionOnTarget(long adminId, Report report, String reason) {
        ReasonReq reasonReq = new ReasonReq(reason);
        switch (report.getTargetType()) {
            case "TEAM" -> adminTeamService.suspendTeam(adminId, report.getTargetId(), reasonReq);
            case "ACTIVITY" -> adminActivityService.takedown(adminId, report.getTargetId(), reasonReq);
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "该举报对象不支持下架/停用");
        }
    }
}
