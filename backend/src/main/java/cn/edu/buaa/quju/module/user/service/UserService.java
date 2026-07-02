package cn.edu.buaa.quju.module.user.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.user.dto.UserDtos;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UpdateProfileReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserVO;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.entity.UserInterestTag;
import cn.edu.buaa.quju.module.user.mapper.UserInterestTagMapper;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final UserInterestTagMapper tagMapper;

    public UserService(UserMapper userMapper, UserInterestTagMapper tagMapper) {
        this.userMapper = userMapper;
        this.tagMapper = tagMapper;
    }

    public UserVO getProfile(long userId) {
        User u = requireUser(userId);
        return toVO(u);
    }

    public UserVO getPublicProfile(long userId) {
        User u = requireUser(userId);
        return toVO(u);
    }

    @Transactional
    public UserVO updateProfile(long userId, UpdateProfileReq req) {
        User u = requireUser(userId);
        if (req.accountId() != null && !req.accountId().equals(u.getAccountId())) {
            if (req.accountId().length() < 4 || req.accountId().length() > 32)
                throw new BizException(ErrorCode.BAD_REQUEST);
            Long taken = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getAccountId, req.accountId()).ne(User::getId, userId));
            if (taken != null && taken > 0) throw new BizException(ErrorCode.CONFLICT);
            u.setAccountId(req.accountId());
        }
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

        if (req.interestTags() != null) {
            tagMapper.delete(Wrappers.<UserInterestTag>lambdaQuery().eq(UserInterestTag::getUserId, userId));
            req.interestTags().stream().distinct().forEach(tag -> {
                UserInterestTag t = new UserInterestTag();
                t.setUserId(userId);
                t.setTag(tag);
                tagMapper.insert(t);
            });
        }
        return toVO(u);
    }

    /** 我的二维码内容（供加好友扫码）：返回 userId，前端生成实际二维码图片。 */
    public String getQrCodeContent(long userId) {
        return "quju://user/" + userId;
    }

    // ---- 私有工具 ----

    private User requireUser(long userId) {
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return u;
    }

    private UserVO toVO(User u) {
        List<String> tags = tagMapper.selectList(
                Wrappers.<UserInterestTag>lambdaQuery().eq(UserInterestTag::getUserId, u.getId()))
                .stream().map(UserInterestTag::getTag).collect(Collectors.toList());
        return new UserVO(u.getId(), u.getAccountId(), u.getEmail(), u.getNickname(), u.getAvatar(), u.getUserType(),
                u.getStatus(), u.getGender(), u.getBirthday(), u.getSignature(), u.getReputation(), tags);
    }

    public UserDtos.UserBrief searchByAccountId(String accountId) {
        User u = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getAccountId, accountId).isNull(User::getDeletedAt));
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND);
        return new UserDtos.UserBrief(u.getId(), u.getAccountId(), u.getNickname(), u.getAvatar(), u.getUserType(), u.getStatus());
    }
}
