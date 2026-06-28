package cn.edu.buaa.quju.module.admin.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("user_ban")
public class UserBan {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String reason;
    private LocalDateTime banUntil;
    private String status;
    private Long operatorAdminId;
    private LocalDateTime liftedAt;
    private LocalDateTime createdAt;
}
