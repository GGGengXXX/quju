package cn.edu.buaa.quju.module.user.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.user.dto.UserDtos.MerchantApplyReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.MerchantUpdateReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.MerchantVO;
import cn.edu.buaa.quju.module.user.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家资料（需登录）。 */
@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {
    private final MerchantService merchantService;
    public MerchantController(MerchantService merchantService) { this.merchantService = merchantService; }

    @PostMapping("/apply")
    public R<Void> apply(@RequestBody @Valid MerchantApplyReq req) {
        merchantService.apply(UserContext.require(), req);
        return R.<Void>ok(null);
    }

    @GetMapping("/me")
    public R<MerchantVO> getMyProfile() {
        return R.ok(merchantService.getMyProfile(UserContext.require()));
    }

    @PutMapping("/me")
    public R<MerchantVO> updateProfile(@RequestBody MerchantUpdateReq req) {
        return R.ok(merchantService.updateProfile(UserContext.require(), req));
    }
}
