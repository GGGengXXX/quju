package cn.edu.buaa.quju.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("activity_tag")
public class ActivityTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String tag;
}
