package cn.edu.buaa.quju.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 用户/鉴权模块 DTO（请求体校验 + 响应视图）。字段对齐 contracts/openapi.yaml。 */
public final class UserDtos {
    private UserDtos() {}

    // ---- 注册 / 激活 / 登录 ----
    public record RegisterReq(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank String userType,
            String licenseUrl,
            String merchantName) {}

    public record ActivateReq(@NotBlank String token) {}

    public record LoginReq(@NotBlank @Email String email, @NotBlank String password) {}

    // ---- 密码重置 ----
    public record PasswordResetRequestReq(@NotBlank @Email String email) {}

    public record PasswordResetReq(@NotBlank String token,
                                   @NotBlank @Size(min = 8) String newPassword) {}

    // ---- 用户资料响应 ----
    public record UserBrief(Long id, String accountId, String nickname, String avatar, String userType, String status) {}

    public record LoginResp(String token, long expiresIn, UserBrief user) {}

    public record UserAiSettings(String systemPrompt) {}

    public record UserVO(Long id, String accountId, String email, String nickname, String avatar, String userType,
                         String status, String gender, LocalDate birthday, String signature,
                         Integer reputation, List<String> interestTags, Map<String, Boolean> privacySettings,
                         UserAiSettings aiSettings) {}

    public record UpdateProfileReq(String accountId, String nickname, String avatar, String gender,
                                   LocalDate birthday, String signature,
                                   List<String> interestTags, Map<String, Boolean> privacySettings,
                                   UserAiSettings aiSettings) {}

    // ---- 商家 ----
    public record MerchantApplyReq(@NotBlank String merchantName, String nickname,
                                   String focusFields, @NotBlank String licenseUrl) {}

    public record MerchantUpdateReq(String merchantName, String nickname, String focusFields, String licenseUrl) {}

    public record MerchantVO(Long id, Long userId, String merchantName, String nickname,
                              String focusFields, String licenseUrl,
                              String auditStatus, String auditReason,
                              LocalDateTime createdAt) {}
}
