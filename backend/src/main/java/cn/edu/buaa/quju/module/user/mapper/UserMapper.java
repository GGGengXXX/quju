package cn.edu.buaa.quju.module.user.mapper;

import cn.edu.buaa.quju.module.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
