package cn.edu.buaa.quju.module.admin.controller;

import cn.edu.buaa.quju.common.AdminContext;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.AdminLoginReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.AdminLoginResp;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ChangePasswordReq;
import cn.edu.buaa.quju.module.admin.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
public class AdminAuthController {
    private final AdminAuthService authService;
    public AdminAuthController(AdminAuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public R<AdminLoginResp> login(@RequestBody @Valid AdminLoginReq req) {
        return R.ok(authService.login(req));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody @Valid ChangePasswordReq req) {
        authService.changePassword(AdminContext.require(), req);
        return R.<Void>ok(null);
    }
}
