package cn.edu.buaa.quju.module.user.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.user.dto.UserDtos.ActivateReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.LoginReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.LoginResp;
import cn.edu.buaa.quju.module.user.dto.UserDtos.PasswordResetReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.PasswordResetRequestReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.RegisterReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserBrief;
import cn.edu.buaa.quju.module.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 鉴权：注册 / 激活 / 登录 / 密码重置。公开接口（无需 Bearer）。 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public R<UserBrief> register(@RequestBody @Valid RegisterReq req) {
        return R.ok(authService.register(req));
    }

    @PostMapping("/activate")
    public R<Void> activate(@RequestBody @Valid ActivateReq req) {
        authService.activate(req.token());
        return R.<Void>ok(null);
    }

    @PostMapping("/login")
    public R<LoginResp> login(@RequestBody @Valid LoginReq req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/password/reset-request")
    public R<Void> requestPasswordReset(@RequestBody @Valid PasswordResetRequestReq req) {
        authService.requestPasswordReset(req);
        return R.<Void>ok(null);
    }

    @PostMapping("/password/reset")
    public R<Void> resetPassword(@RequestBody @Valid PasswordResetReq req) {
        authService.resetPassword(req);
        return R.<Void>ok(null);
    }
}
