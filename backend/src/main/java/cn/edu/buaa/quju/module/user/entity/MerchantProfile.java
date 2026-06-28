package cn.edu.buaa.quju.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("merchant_profile")
public class MerchantProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String merchantName;
    private String nickname;
    private String focusFields;
    private String licenseUrl;
    private String auditStatus;   // PENDING | APPROVED | REJECTED
    private String auditReason;
    private Long auditAdminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
