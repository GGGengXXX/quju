package cn.edu.buaa.quju.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 用户/鉴权模块 DTO（请求体校验 + 响应视图）。字段对齐 contracts/openapi.yaml。 */
public final class UserDtos {
    private UserDtos() {}

    public record RegisterReq(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank String userType,
            String licenseUrl) {}

    public record ActivateReq(@NotBlank String token) {}

    public record LoginReq(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserBrief(Long id, String nickname, String avatar, String userType, String status) {}

    public record LoginResp(String token, long expiresIn, UserBrief user) {}

    public record UserVO(Long id, String email, String nickname, String avatar, String userType,
                         String status, String gender, LocalDate birthday, String signature,
                         Integer reputation, List<String> interestTags) {}

    public record UpdateProfileReq(String nickname, String avatar, String gender,
                                   LocalDate birthday, String signature) {}
}
