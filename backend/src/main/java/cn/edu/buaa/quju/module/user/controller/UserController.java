package cn.edu.buaa.quju.module.user.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UpdateProfileReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserVO;
import cn.edu.buaa.quju.module.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户资料（受保护接口）。 */
@RestController
@RequestMapping("/v1/users")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.getProfile(UserContext.require()));
    }

    @PutMapping("/me")
    public R<UserVO> update(@RequestBody UpdateProfileReq req) {
        return R.ok(userService.updateProfile(UserContext.require(), req));
    }

    @GetMapping("/me/qrcode")
    public R<String> qrcode() {
        return R.ok(userService.getQrCodeContent(UserContext.require()));
    }

    @GetMapping("/{id}")
    public R<UserVO> getById(@PathVariable Long id) {
        // 公开接口：可选鉴权（UserContext.get() 取当前用户，null 则匿名）
        return R.ok(userService.getPublicProfile(id));
    }
}
