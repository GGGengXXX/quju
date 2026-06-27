package cn.edu.buaa.quju.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String userType;   // INDIVIDUAL | MERCHANT
    private String status;     // PENDING_ACTIVATION | ACTIVE | BANNED
    private String nickname;
    private String avatar;
    private String gender;     // MALE | FEMALE | UNKNOWN
    private LocalDate birthday;
    private String signature;
    private Integer reputation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;   // 软删；查询默认过滤 deleted_at IS NULL
}
