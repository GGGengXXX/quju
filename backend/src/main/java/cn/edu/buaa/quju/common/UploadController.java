package cn.edu.buaa.quju.common;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1/upload")
public class UploadController {
    private final OssService ossService;

    public UploadController(OssService ossService) {
        this.ossService = ossService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        long userId = UserContext.require();
        String url = ossService.uploadImage("user/" + userId + "/images", file);
        return R.ok(Map.of("url", url));
    }

    /**
     * 营业执照/凭证上传：注册阶段（未登录）也要能用，故不强制登录。
     * 已登录则按用户分目录，未登录归入公共 license 目录。
     */
    @PostMapping(value = "/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, String>> uploadLicense(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.get();
        String prefix = userId != null ? "merchant/license/" + userId : "merchant/license/anon";
        String url = ossService.uploadImage(prefix, file);
        return R.ok(Map.of("url", url));
    }
}
