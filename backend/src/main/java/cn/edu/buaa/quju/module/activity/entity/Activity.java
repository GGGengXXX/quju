package cn.edu.buaa.quju.module.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private Long teamId;
    private String name;
    private String intro;
    private String category;
    private String coverImage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime signupDeadline;
    private String city;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private Integer capacity;
    private BigDecimal fee;
    private String status;
    private Boolean isAiGenerated;
    private Long templateId;
    private Long clonedFromId;
    private String checkinCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
