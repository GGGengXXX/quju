package cn.edu.buaa.quju.module.admin.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("moderation_action")
public class ModerationAction {
    @TableId(type = IdType.AUTO) private Long id;
    private String targetType;
    private Long targetId;
    private String action;
    private String reason;
    private Long adminId;
    private LocalDateTime createdAt;
}
