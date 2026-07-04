package cn.edu.buaa.quju.module.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ReportDtos {
    private ReportDtos() {}

    /** 用户提交举报（targetType 仅 ACTIVITY|TEAM） */
    public record ReportCreateReq(@NotBlank String targetType,
                                  @NotNull @Positive Long targetId,
                                  @NotBlank @Size(max = 64) String reason,
                                  @Size(max = 500) String detail) {}
}
