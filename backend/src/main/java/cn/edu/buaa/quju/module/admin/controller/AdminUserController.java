package cn.edu.buaa.quju.module.admin.controller;

import cn.edu.buaa.quju.common.AdminContext;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.BanReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.MerchantAppVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.MerchantReviewReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserDetailVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserListVO;
import cn.edu.buaa.quju.module.admin.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
public class AdminUserController {
    private final AdminUserService userService;
    public AdminUserController(AdminUserService userService) { this.userService = userService; }

    @GetMapping("/users")
    public R<PageResult<UserListVO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminContext.require();
        return R.ok(userService.listUsers(keyword, userType, page, size));
    }

    @GetMapping("/users/{id}")
    public R<UserDetailVO> getUserDetail(@PathVariable Long id) {
        AdminContext.require();
        return R.ok(userService.getUserDetail(id));
    }

    @PostMapping("/users/{id}/ban")
    public R<Void> banUser(@PathVariable Long id, @RequestBody @Valid BanReq req) {
        userService.banUser(AdminContext.require(), id, req);
        return R.<Void>ok(null);
    }

    @PostMapping("/users/{id}/unban")
    public R<Void> unbanUser(@PathVariable Long id) {
        userService.unbanUser(AdminContext.require(), id);
        return R.<Void>ok(null);
    }

    @GetMapping("/merchant-applications")
    public R<PageResult<MerchantAppVO>> listMerchants(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminContext.require();
        return R.ok(userService.listMerchantApplications(status, page, size));
    }

    @PutMapping("/merchant-applications/{id}")
    public R<Void> reviewMerchant(@PathVariable Long id, @RequestBody @Valid MerchantReviewReq req) {
        userService.reviewMerchant(AdminContext.require(), id, req);
        return R.<Void>ok(null);
    }
}
