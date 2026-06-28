package cn.edu.buaa.quju.module.admin.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long creatorId;
    private String name;
    private String category;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime signupDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
