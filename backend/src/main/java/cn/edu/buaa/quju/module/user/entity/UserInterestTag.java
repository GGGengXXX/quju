package cn.edu.buaa.quju.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_interest_tag")
public class UserInterestTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tag;
}
