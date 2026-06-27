package cn.edu.buaa.quju.module.user.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UpdateProfileReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserVO;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class UserService {
    private final UserMapper userMapper;
    public UserService(UserMapper userMapper) { this.userMapper = userMapper; }

    public UserVO getProfile(long userId) {
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return toVO(u);
    }

    @Transactional
    public UserVO updateProfile(long userId, UpdateProfileReq req) {
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        if (req.nickname() != null && !req.nickname().equals(u.getNickname())) {
            Long taken = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getNickname, req.nickname()).ne(User::getId, userId));
            if (taken != null && taken > 0) throw new BizException(ErrorCode.NICKNAME_TAKEN);
            u.setNickname(req.nickname());
        }
        if (req.avatar() != null) u.setAvatar(req.avatar());
        if (req.gender() != null) u.setGender(req.gender());
        if (req.birthday() != null) u.setBirthday(req.birthday());
        if (req.signature() != null) u.setSignature(req.signature());
        userMapper.updateById(u);
        return toVO(u);
    }

    private UserVO toVO(User u) {
        // 兴趣标签由用户模块负责人接 user_interest_tag（此参考切片先返回空数组）
        return new UserVO(u.getId(), u.getEmail(), u.getNickname(), u.getAvatar(), u.getUserType(),
                u.getStatus(), u.getGender(), u.getBirthday(), u.getSignature(), u.getReputation(),
                Collections.emptyList());
    }
}
