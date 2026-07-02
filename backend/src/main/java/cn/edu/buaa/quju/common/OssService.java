package cn.edu.buaa.quju.common;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
public class OssService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    @Value("${quju.oss.endpoint:}")
    private String endpoint;
    @Value("${quju.oss.bucket:}")
    private String bucket;
    @Value("${quju.oss.access-key-id:}")
    private String accessKeyId;
    @Value("${quju.oss.access-key-secret:}")
    private String accessKeySecret;

    public String uploadImage(String prefix, MultipartFile file) {
        if (endpoint.isBlank() || bucket.isBlank())
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        if (file.isEmpty()) throw new BizException(ErrorCode.BAD_REQUEST);
        if (file.getSize() > MAX_IMAGE_SIZE)
            throw new BizException(ErrorCode.BAD_REQUEST);
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_IMAGE_TYPES.contains(contentType))
            throw new BizException(ErrorCode.BAD_REQUEST);

        String ext = extensionFromContentType(contentType);
        String objectKey = prefix + "/" + UUID.randomUUID() + ext;

        OSS oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream is = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType);
            meta.setContentLength(file.getSize());
            PutObjectRequest req = new PutObjectRequest(bucket, objectKey, is, meta);
            oss.putObject(req);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        } finally {
            oss.shutdown();
        }

        String strippedEndpoint = endpoint.replaceFirst("^https?://", "");
        return "https://" + bucket + "." + strippedEndpoint + "/" + objectKey;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            default -> ".bin";
        };
    }
}
