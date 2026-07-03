package cn.edu.buaa.quju.module.activity.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ActivityImageStorageService {
    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp"
    );

    @Value("${quju.oss.endpoint:}")
    private String endpoint;

    @Value("${quju.oss.bucket:}")
    private String bucket;

    @Value("${quju.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${quju.oss.access-key-secret:}")
    private String accessKeySecret;

    public UploadResult uploadSummaryImage(long activityId, long userId, MultipartFile file) {
        validateConfig();
        validateImage(file);

        String contentType = normalizeContentType(file.getContentType());
        String originalName = sanitizeFileName(file.getOriginalFilename());
        String extension = resolveExtension(originalName, contentType);
        String objectKey = buildObjectKey(activityId, userId, extension);
        String url = uploadObject(file, contentType, objectKey);
        return new UploadResult(url, originalName, file.getSize());
    }

    public void deleteImageByUrl(String url) {
        validateConfig();
        if (isBlank(url)) return;
        String objectKey = toObjectKey(url);
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(normalizeEndpoint(endpoint), accessKeyId, accessKeySecret);
            client.deleteObject(bucket, objectKey);
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "oss_delete_failed");
        } finally {
            if (client != null) client.shutdown();
        }
    }

    private String uploadObject(MultipartFile file, String contentType, String objectKey) {
        OSS client = null;
        try (InputStream inputStream = file.getInputStream()) {
            client = new OSSClientBuilder().build(normalizeEndpoint(endpoint), accessKeyId, accessKeySecret);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType.isBlank() ? "application/octet-stream" : contentType);
            if (file.getSize() > 0) metadata.setContentLength(file.getSize());
            PutObjectRequest request = new PutObjectRequest(bucket, objectKey, inputStream, metadata);
            client.putObject(request);
            client.setObjectAcl(bucket, objectKey, CannedAccessControlList.PublicRead);
            return buildPublicUrl(objectKey);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "oss_upload_failed");
        } finally {
            if (client != null) client.shutdown();
        }
    }

    private void validateConfig() {
        if (isBlank(endpoint) || isBlank(bucket) || isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "oss_not_configured");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BizException(ErrorCode.BAD_REQUEST, "请选择图片");
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持 JPG/PNG/GIF/WEBP/BMP 图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片大小不能超过 20MB");
        }
    }

    private String buildObjectKey(long activityId, long userId, String extension) {
        return "activity/%d/%d/summary/%s%s".formatted(activityId, userId, UUID.randomUUID(), extension);
    }

    private String buildPublicUrl(String objectKey) {
        return "https://%s.%s/%s".formatted(bucket, stripProtocol(endpoint), objectKey);
    }

    private String normalizeEndpoint(String raw) {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        return "https://" + raw;
    }

    private String stripProtocol(String raw) {
        return raw.replaceFirst("^https?://", "");
    }

    private String sanitizeFileName(String originalName) {
        if (isBlank(originalName)) return "image";
        return originalName.replace("\\", "/").replaceAll("^.*/", "").trim();
    }

    private String resolveExtension(String fileName, String contentType) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/jpeg" -> ".jpg";
            default -> ".bin";
        };
    }

    private String normalizeContentType(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String toObjectKey(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.length() <= 1) throw new IllegalArgumentException("invalid path");
            return path.substring(1);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片地址非法");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record UploadResult(String url, String fileName, Long fileSize) {}
}
