package cn.edu.buaa.quju.module.report.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.report.dto.ReportDtos.ReportCreateReq;
import cn.edu.buaa.quju.module.report.entity.Report;
import cn.edu.buaa.quju.module.report.mapper.ReportMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ReportService {
    /** 当前仅支持举报活动与小队。 */
    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("ACTIVITY", "TEAM");

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public void create(ReportCreateReq req) {
        long reporterId = UserContext.require();
        String targetType = req.targetType() == null ? null : req.targetType().trim().toUpperCase();
        if (targetType == null || !ALLOWED_TARGET_TYPES.contains(targetType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "举报对象类型非法");
        }
        // 同一举报人对同一对象存在未处理举报时，不重复提交
        boolean duplicated = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, req.targetId())
                .eq(Report::getStatus, "PENDING")) > 0;
        if (duplicated) throw new BizException(ErrorCode.CONFLICT, "该对象已有待处理的举报");

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(req.targetId());
        report.setReason(req.reason().trim());
        report.setDetail(req.detail() == null || req.detail().isBlank() ? null : req.detail().trim());
        report.setStatus("PENDING");
        reportMapper.insert(report);
    }
}
