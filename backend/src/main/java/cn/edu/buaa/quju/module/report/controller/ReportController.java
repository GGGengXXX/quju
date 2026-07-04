package cn.edu.buaa.quju.module.report.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.report.dto.ReportDtos.ReportCreateReq;
import cn.edu.buaa.quju.module.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ReportController {
    private final ReportService reportService;
    public ReportController(ReportService reportService) { this.reportService = reportService; }

    @PostMapping("/reports")
    public R<Void> create(@RequestBody @Valid ReportCreateReq req) {
        reportService.create(req);
        return R.<Void>ok(null);
    }
}
