package cn.edu.buaa.quju.module.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scope;         // FRIEND|TEAM
    private Long senderId;
    private Long receiverId;      // scope=FRIEND
    private Long teamId;          // scope=TEAM
    private String contentType;   // TEXT|EMOJI|IMAGE|LOCATION
    private String content;
    private Boolean isRead;
    private Boolean isRecalled;
    private LocalDateTime recalledAt;
    private Long forwardedFromId;
    private LocalDateTime createdAt;
}
