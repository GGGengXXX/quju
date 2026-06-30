package cn.edu.buaa.quju.module.activity.dto;

import cn.edu.buaa.quju.module.user.dto.UserDtos.UserBrief;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ActivityDtos {
    private ActivityDtos() {}

    public record TemplateVO(
            Long id,
            String name,
            String category,
            String defaultIntro,
            Integer defaultCapacity,
            String icon,
            Boolean isSystem
    ) {}

    public record ActivityVO(
            Long id,
            String name,
            String intro,
            String category,
            List<String> tags,
            String coverImage,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime signupDeadline,
            String city,
            String address,
            BigDecimal lng,
            BigDecimal lat,
            Integer capacity,
            BigDecimal fee,
            String status,
            String phase,
            Integer signupCount,
            UserBrief creator,
            Long teamId
    ) {}

    public record ActivityDetailVO(
            Long id,
            String name,
            String intro,
            String category,
            List<String> tags,
            String coverImage,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime signupDeadline,
            String city,
            String address,
            BigDecimal lng,
            BigDecimal lat,
            Integer capacity,
            BigDecimal fee,
            String status,
            String phase,
            Integer signupCount,
            UserBrief creator,
            Long teamId,
            String mySignupStatus,
            Integer waitlistCount
    ) {}

    public record ActivityPointVO(
            Long id,
            String name,
            String category,
            BigDecimal lng,
            BigDecimal lat,
            String city,
            String status,
            String phase
    ) {}

    public record ActivityUpsertReq(
            @NotBlank String name,
            String intro,
            String category,
            List<String> tags,
            String coverImage,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime signupDeadline,
            String city,
            String address,
            BigDecimal lng,
            BigDecimal lat,
            Integer capacity,
            BigDecimal fee,
            Long teamId,
            Boolean submit
    ) {}

    public record AiPlanReq(
            String theme,
            String category
    ) {}

    public record SignupReq(Map<String, Object> signupInfo) {}

    public record SignupResultVO(String status, Integer waitlistPosition) {}

    public record SignupManageVO(
            Long signupId,
            Long userId,
            String nickname,
            String signupStatus,
            Boolean checkedIn,
            LocalDateTime signupAt,
            LocalDateTime checkinAt
    ) {}

    public record WaitlistVO(
            Long id,
            Long userId,
            String nickname,
            Integer position,
            String status,
            LocalDateTime notifiedAt,
            LocalDateTime confirmDeadline
    ) {}

    public record WaitlistPageVO(Integer waitlistCount, List<WaitlistVO> list) {}

    public record CheckinCodeVO(String code) {}

    public record CheckinReq(
            @NotBlank String code,
            BigDecimal lng,
            BigDecimal lat
    ) {}

    public record SummaryVO(
            Long id,
            Long activityId,
            Long authorId,
            String content,
            String status,
            List<SummaryImageVO> images
    ) {}

    public record SummaryImageVO(
            Long id,
            String imageUrl,
            String aiCategory,
            String confirmedCategory,
            Boolean confirmed
    ) {}

    public record SummaryUpsertReq(
            String content,
            Boolean publish
    ) {}

    public record SummaryImageUploadReq(
            @NotEmpty List<String> imageUrls
    ) {}

    public record SummaryImageUpdateReq(
            @NotBlank String category
    ) {}

    public record ReviewUpsertReq(
            @NotNull @Min(1) @Max(5) Integer rating,
            String content
    ) {}

    public record ReviewVO(
            Long id,
            Long userId,
            String nickname,
            Integer rating,
            String content,
            LocalDateTime createdAt
    ) {}
}
