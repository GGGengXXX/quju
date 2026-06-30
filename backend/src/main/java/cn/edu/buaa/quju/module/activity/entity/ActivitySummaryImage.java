package cn.edu.buaa.quju.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_summary_image")
public class ActivitySummaryImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String imageUrl;
    private String aiCategory;
    private String confirmedCategory;
    private Boolean confirmed;
    private LocalDateTime createdAt;
}
