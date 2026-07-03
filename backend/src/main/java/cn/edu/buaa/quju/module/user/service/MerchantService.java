package cn.edu.buaa.quju.module.user.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.user.dto.UserDtos.MerchantApplyReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.MerchantUpdateReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.MerchantVO;
import cn.edu.buaa.quju.module.user.entity.MerchantProfile;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.MerchantProfileMapper;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {
    private final MerchantProfileMapper merchantMapper;
    private final UserMapper userMapper;

    public MerchantService(MerchantProfileMapper merchantMapper, UserMapper userMapper) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public void apply(long userId, MerchantApplyReq req) {
        User u = userMapper.selectById(userId);
        if (u == null || u.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);

        // 已有申请则覆盖（重新提交）
        MerchantProfile existing = merchantMapper.selectOne(
                Wrappers.<MerchantProfile>lambdaQuery().eq(MerchantProfile::getUserId, userId));
        if (existing != null) {
            existing.setMerchantName(req.merchantName());
            existing.setNickname(req.nickname());
            existing.setFocusFields(req.focusFields());
            existing.setLicenseUrl(req.licenseUrl());
            existing.setAuditStatus("PENDING");
            existing.setAuditReason(null);
            merchantMapper.updateById(existing);
        } else {
            MerchantProfile mp = new MerchantProfile();
            mp.setUserId(userId);
            mp.setMerchantName(req.merchantName());
            mp.setNickname(req.nickname());
            mp.setFocusFields(req.focusFields());
            mp.setLicenseUrl(req.licenseUrl());
            mp.setAuditStatus("PENDING");
            merchantMapper.insert(mp);
            // 商家身份需后台审核通过后由管理员授予（见 AdminUserService#reviewMerchant），
            // 提交申请阶段不提前升级 userType。
        }
    }

    public MerchantVO getMyProfile(long userId) {
        MerchantProfile mp = findByUserId(userId);
        // 尚无档案（如历史商家账号）时返回空档，前端可加载并引导补交，而非报"资源不存在"。
        if (mp == null) return new MerchantVO(null, userId, null, null, null, null, null, null, null);
        return toVO(mp);
    }

    @Transactional
    public MerchantVO updateProfile(long userId, MerchantUpdateReq req) {
        MerchantProfile mp = findByUserId(userId);
        if (mp == null) {
            // upsert：缺档案时按本次填写建档并进入后台审核（PENDING）。
            if (req.merchantName() == null || req.merchantName().isBlank())
                throw new BizException(ErrorCode.MERCHANT_NAME_REQUIRED);
            mp = new MerchantProfile();
            mp.setUserId(userId);
            mp.setMerchantName(req.merchantName());
            mp.setNickname(req.nickname());
            mp.setFocusFields(req.focusFields());
            mp.setAuditStatus("PENDING");
            merchantMapper.insert(mp);
            return toVO(mp);
        }
        if (req.merchantName() != null) mp.setMerchantName(req.merchantName());
        if (req.nickname() != null) mp.setNickname(req.nickname());
        if (req.focusFields() != null) mp.setFocusFields(req.focusFields());
        merchantMapper.updateById(mp);
        return toVO(mp);
    }

    private MerchantProfile findByUserId(long userId) {
        return merchantMapper.selectOne(
                Wrappers.<MerchantProfile>lambdaQuery().eq(MerchantProfile::getUserId, userId));
    }

    private MerchantVO toVO(MerchantProfile mp) {
        return new MerchantVO(mp.getId(), mp.getUserId(), mp.getMerchantName(), mp.getNickname(),
                mp.getFocusFields(), mp.getLicenseUrl(), mp.getAuditStatus(), mp.getAuditReason(),
                mp.getCreatedAt());
    }
}
