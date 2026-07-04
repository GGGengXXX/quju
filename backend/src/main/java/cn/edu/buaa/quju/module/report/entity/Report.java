package cn.edu.buaa.quju.module.report.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("report")
public class Report {
    @TableId(type = IdType.AUTO) private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private String status;
    private Long handlerAdminId;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
