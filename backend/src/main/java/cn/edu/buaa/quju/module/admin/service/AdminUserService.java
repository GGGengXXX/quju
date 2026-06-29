package cn.edu.buaa.quju.module.admin.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.BanReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.MerchantAppVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.MerchantReviewReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserDetailVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserListVO;
import cn.edu.buaa.quju.module.admin.entity.UserBan;
import cn.edu.buaa.quju.module.admin.mapper.UserBanMapper;
import cn.edu.buaa.quju.module.user.entity.MerchantProfile;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.MerchantProfileMapper;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private final UserMapper userMapper;
    private final UserBanMapper banMapper;
    private final MerchantProfileMapper merchantMapper;

    public AdminUserService(UserMapper userMapper, UserBanMapper banMapper,
                            MerchantProfileMapper merchantMapper) {
        this.userMapper = userMapper;
        this.banMapper = banMapper;
        this.merchantMapper = merchantMapper;
    }

    public PageResult<UserListVO> listUsers(String keyword, String userType, int page, int size) {
        LambdaQueryWrapper<User> q = Wrappers.<User>lambdaQuery().isNull(User::getDeletedAt);
        if (keyword != null && !keyword.isBlank())
            q.and(w -> w.like(User::getEmail, keyword).or().like(User::getNickname, keyword));
        if (userType != null && !userType.isBlank())
            q.eq(User::getUserType, userType);
        IPage<User> p = userMapper.selectPage(new Page<>(page, size), q);
        List<UserListVO> list = p.getRecords().stream()
                .map(u -> new UserListVO(u.getId(), u.getEmail(), u.getNickname(),
                        u.getUserType(), u.getStatus(), u.getCreatedAt()))
                .collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), page, size, list);
    }

    public UserDetailVO getUserDetail(long userId) {
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return new UserDetailVO(u.getId(), u.getEmail(), u.getNickname(), u.getAvatar(),
                u.getUserType(), u.getStatus(), u.getGender(), u.getBirthday(),
                u.getSignature(), u.getReputation(), u.getCreatedAt(), u.getUpdatedAt());
    }

    @Transactional
    public void banUser(long adminId, long userId, BanReq req) {
        if (req.reason() == null || req.reason().isBlank())
            throw new BizException(ErrorCode.BAN_REASON_REQUIRED);
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);

        // 先解除旧封禁
        banMapper.update(null, Wrappers.<UserBan>lambdaUpdate()
                .eq(UserBan::getUserId, userId).eq(UserBan::getStatus, "ACTIVE")
                .set(UserBan::getStatus, "LIFTED").set(UserBan::getLiftedAt, LocalDateTime.now()));

        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setReason(req.reason());
        ban.setBanUntil(req.banUntil());
        ban.setStatus("ACTIVE");
        ban.setOperatorAdminId(adminId);
        banMapper.insert(ban);

        u.setStatus("BANNED");
        userMapper.updateById(u);
    }

    @Transactional
    public void unbanUser(long adminId, long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException(ErrorCode.NOT_FOUND);

        banMapper.update(null, Wrappers.<UserBan>lambdaUpdate()
                .eq(UserBan::getUserId, userId).eq(UserBan::getStatus, "ACTIVE")
                .set(UserBan::getStatus, "LIFTED").set(UserBan::getLiftedAt, LocalDateTime.now()));

        u.setStatus("ACTIVE");
        userMapper.updateById(u);
    }

    public PageResult<MerchantAppVO> listMerchantApplications(String status, int page, int size) {
        LambdaQueryWrapper<MerchantProfile> q = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) q.eq(MerchantProfile::getAuditStatus, status);
        IPage<MerchantProfile> p = merchantMapper.selectPage(new Page<>(page, size), q);
        List<MerchantAppVO> list = p.getRecords().stream()
                .map(m -> new MerchantAppVO(m.getId(), m.getUserId(), m.getMerchantName(),
                        m.getLicenseUrl(), m.getAuditStatus(), m.getAuditReason(), m.getCreatedAt()))
                .collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), page, size, list);
    }

    @Transactional
    public void reviewMerchant(long adminId, long merchantId, MerchantReviewReq req) {
        MerchantProfile mp = merchantMapper.selectById(merchantId);
        if (mp == null) throw new BizException(ErrorCode.NOT_FOUND);
        if ("REJECTED".equals(req.action()) && (req.reason() == null || req.reason().isBlank()))
            throw new BizException(ErrorCode.REJECT_REASON_REQUIRED);
        mp.setAuditStatus("APPROVED".equals(req.action()) ? "APPROVED" : "REJECTED");
        mp.setAuditReason(req.reason());
        mp.setAuditAdminId(adminId);
        merchantMapper.updateById(mp);
    }
}
