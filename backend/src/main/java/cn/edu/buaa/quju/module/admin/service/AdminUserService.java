package cn.edu.buaa.quju.module.admin.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.BanReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.MerchantAppVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.MerchantReviewReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserActivityBriefVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserDetailVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserListVO;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.UserTeamBriefVO;
import cn.edu.buaa.quju.module.admin.entity.Activity;
import cn.edu.buaa.quju.module.admin.entity.Team;
import cn.edu.buaa.quju.module.admin.entity.UserBan;
import cn.edu.buaa.quju.module.admin.mapper.ActivityMapper;
import cn.edu.buaa.quju.module.admin.mapper.TeamMapper;
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
    private final ActivityMapper activityMapper;
    private final TeamMapper teamMapper;

    public AdminUserService(UserMapper userMapper, UserBanMapper banMapper,
                            MerchantProfileMapper merchantMapper,
                            ActivityMapper activityMapper, TeamMapper teamMapper) {
        this.userMapper = userMapper;
        this.banMapper = banMapper;
        this.merchantMapper = merchantMapper;
        this.activityMapper = activityMapper;
        this.teamMapper = teamMapper;
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

        // 其发布的活动（最近 50 条）
        List<UserActivityBriefVO> activities = activityMapper.selectList(
                Wrappers.<Activity>lambdaQuery()
                        .eq(Activity::getCreatorId, userId)
                        .isNull(Activity::getDeletedAt)
                        .orderByDesc(Activity::getCreatedAt)
                        .last("limit 50"))
                .stream()
                .map(a -> new UserActivityBriefVO(a.getId(), a.getName(), a.getStatus(), a.getStartTime()))
                .collect(Collectors.toList());

        // 其创建的小队（作为队长）
        List<UserTeamBriefVO> teams = teamMapper.selectList(
                Wrappers.<Team>lambdaQuery()
                        .eq(Team::getOwnerId, userId)
                        .isNull(Team::getDeletedAt)
                        .orderByDesc(Team::getCreatedAt))
                .stream()
                .map(t -> new UserTeamBriefVO(t.getId(), t.getName(), t.getStatus(), t.getMemberCount()))
                .collect(Collectors.toList());

        return new UserDetailVO(u.getId(), u.getEmail(), u.getNickname(), u.getAvatar(),
                u.getUserType(), u.getStatus(), u.getGender(), u.getBirthday(),
                u.getSignature(), u.getReputation(), u.getCreatedAt(), u.getUpdatedAt(),
                activities, teams);
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
        // 契约 action 枚举为 APPROVE / REJECT
        String action = req.action() == null ? "" : req.action().trim().toUpperCase();
        boolean approve = "APPROVE".equals(action);
        boolean reject = "REJECT".equals(action);
        if (!approve && !reject) throw new BizException(ErrorCode.BAD_REQUEST, "审核动作非法");
        if (reject && (req.reason() == null || req.reason().isBlank()))
            throw new BizException(ErrorCode.REJECT_REASON_REQUIRED);

        mp.setAuditStatus(approve ? "APPROVED" : "REJECTED");
        mp.setAuditReason(req.reason());
        mp.setAuditAdminId(adminId);
        merchantMapper.updateById(mp);

        // 商家身份由后台审核结果授予/收回：通过 -> MERCHANT，驳回 -> 保持个人身份
        User applicant = userMapper.selectById(mp.getUserId());
        if (applicant != null && applicant.getDeletedAt() == null) {
            applicant.setUserType(approve ? "MERCHANT" : "INDIVIDUAL");
            userMapper.updateById(applicant);
        }
    }
}
