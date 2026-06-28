package cn.edu.buaa.quju.module.admin.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.JwtUtil;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.AdminLoginReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.AdminLoginResp;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ChangePasswordReq;
import cn.edu.buaa.quju.module.admin.entity.Admin;
import cn.edu.buaa.quju.module.admin.mapper.AdminMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private final AdminMapper adminMapper;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminAuthService(AdminMapper adminMapper, JwtUtil jwt) {
        this.adminMapper = adminMapper;
        this.jwt = jwt;
    }

    public AdminLoginResp login(AdminLoginReq req) {
        Admin admin = adminMapper.selectOne(Wrappers.<Admin>lambdaQuery()
                .eq(Admin::getUsername, req.username()));
        if (admin == null || !encoder.matches(req.password(), admin.getPasswordHash()))
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        String token = jwt.generateAdmin(admin.getId());
        return new AdminLoginResp(token, jwt.getExpireSeconds(), admin.getUsername());
    }

    @Transactional
    public void changePassword(long adminId, ChangePasswordReq req) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!encoder.matches(req.oldPassword(), admin.getPasswordHash()))
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        admin.setPasswordHash(encoder.encode(req.newPassword()));
        adminMapper.updateById(admin);
    }
}
