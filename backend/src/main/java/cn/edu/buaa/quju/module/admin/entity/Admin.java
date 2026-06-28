package cn.edu.buaa.quju.module.admin.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("admin")
public class Admin {
    @TableId(type = IdType.AUTO) private Long id;
    private String username;
    private String passwordHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
