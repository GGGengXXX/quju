package cn.edu.buaa.quju.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AdminDtos {
    private AdminDtos() {}

    // ---- 登录/密码 ----
    public record AdminLoginReq(@NotBlank String username, @NotBlank String password) {}
    public record AdminLoginResp(String token, long expiresIn, String username) {}
    public record ChangePasswordReq(@NotBlank String oldPassword,
                                    @NotBlank @Size(min = 8) String newPassword) {}

    // ---- 用户管理 ----
    public record UserListVO(Long id, String email, String nickname, String userType,
                              String status, LocalDateTime createdAt) {}
    public record BanReq(String reason, LocalDateTime banUntil) {}
    public record UserDetailVO(Long id, String email, String nickname, String avatar,
                                String userType, String status, String gender, LocalDate birthday,
                                String signature, Integer reputation,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {}

    // ---- 商家审核 ----
    public record MerchantReviewReq(@NotBlank String action, String reason) {}
    public record MerchantAppVO(Long id, Long userId, String merchantName, String licenseUrl,
                                 String auditStatus, String auditReason, LocalDateTime createdAt) {}

    // ---- 活动管理 ----
    public record ActivityListVO(Long id, Long creatorId, String creatorNickname, String name, String category,
                                  String status, LocalDateTime startTime, LocalDateTime createdAt) {}
    public record ActivityReviewReq(@NotBlank String result, String reason) {}
    public record ReasonReq(String reason) {}

    // ---- 小队管理 ----
    public record TeamListVO(Long id, Long ownerId, String name, String status,
                              Integer memberCount, LocalDateTime createdAt) {}

    // ---- 举报 ----
    public record ReportVO(Long id, Long reporterId, String targetType, Long targetId,
                            String reason, String detail, String status, LocalDateTime createdAt) {}

    // ---- 通用分页 ----
    public record PageResult<T>(long total, int page, int size, java.util.List<T> list) {}
}
