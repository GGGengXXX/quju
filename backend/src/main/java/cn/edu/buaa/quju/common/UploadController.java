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
}
