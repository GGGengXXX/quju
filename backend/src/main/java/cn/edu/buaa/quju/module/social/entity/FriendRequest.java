package cn.edu.buaa.quju.module.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("friend_request")
public class FriendRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String status;   // PENDING|ACCEPTED|REJECTED
    private String source;   // PROFILE|ACTIVITY|QRCODE
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
