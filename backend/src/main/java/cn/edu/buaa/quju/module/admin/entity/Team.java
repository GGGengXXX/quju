package cn.edu.buaa.quju.module.admin.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("team")
public class Team {
    @TableId(type = IdType.AUTO) private Long id;
    private Long ownerId;
    private String name;
    private String status;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
