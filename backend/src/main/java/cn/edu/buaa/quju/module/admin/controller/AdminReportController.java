package cn.edu.buaa.quju.module.admin.controller;

import cn.edu.buaa.quju.common.AdminContext;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReportHandleReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReportVO;
import cn.edu.buaa.quju.module.admin.service.AdminReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/reports")
public class AdminReportController {
    private final AdminReportService reportService;
    public AdminReportController(AdminReportService reportService) { this.reportService = reportService; }

    @GetMapping
    public R<PageResult<ReportVO>> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminContext.require();
        return R.ok(reportService.listReports(status, page, size));
    }

    @PostMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @RequestBody @Valid ReportHandleReq req) {
        reportService.handleReport(AdminContext.require(), id, req);
        return R.<Void>ok(null);
    }
}
