package cn.edu.buaa.quju.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_audit_log")
public class ActivityAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String auditType;
    private String result;
    private String reason;
    private Long auditorAdminId;
    private LocalDateTime createdAt;
}
