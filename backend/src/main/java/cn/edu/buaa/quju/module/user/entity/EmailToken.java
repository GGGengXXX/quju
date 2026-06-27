package cn.edu.buaa.quju.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_token")
public class EmailToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String token;
    private String type;   // ACTIVATION | RESET_PASSWORD
    private LocalDateTime expiresAt;
    private Boolean used;
    private LocalDateTime createdAt;
}
