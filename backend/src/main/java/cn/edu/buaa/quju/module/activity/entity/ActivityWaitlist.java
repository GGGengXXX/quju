package cn.edu.buaa.quju.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_waitlist")
public class ActivityWaitlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private Integer position;
    private String status;
    private LocalDateTime notifiedAt;
    private LocalDateTime confirmDeadline;
    private LocalDateTime createdAt;
}
