package cn.edu.buaa.quju.module.user.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.JwtUtil;
import cn.edu.buaa.quju.module.user.dto.UserDtos.LoginReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.LoginResp;
import cn.edu.buaa.quju.module.user.dto.UserDtos.RegisterReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserBrief;
import cn.edu.buaa.quju.module.user.entity.EmailToken;
import cn.edu.buaa.quju.module.user.entity.User;
import cn.edu.buaa.quju.module.user.mapper.EmailTokenMapper;
import cn.edu.buaa.quju.module.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final EmailTokenMapper tokenMapper;
    private final EmailService emailService;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, EmailTokenMapper tokenMapper, EmailService emailService, JwtUtil jwt) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.emailService = emailService;
        this.jwt = jwt;
    }

    @Transactional
    public UserBrief register(RegisterReq req) {
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, req.email()).isNull(User::getDeletedAt));
        if (exists != null && exists > 0) throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        if ("MERCHANT".equals(req.userType()) && (req.licenseUrl() == null || req.licenseUrl().isBlank()))
            throw new BizException(ErrorCode.MERCHANT_LICENSE_REQUIRED);

        User u = new User();
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setUserType(req.userType());
        u.setStatus("PENDING_ACTIVATION");
        u.setGender("UNKNOWN");
        u.setReputation(100);
        userMapper.insert(u);

        EmailToken et = new EmailToken();
        et.setUserId(u.getId());
        et.setToken(UUID.randomUUID().toString().replace("-", ""));
        et.setType("ACTIVATION");
        et.setExpiresAt(LocalDateTime.now().plusHours(24));
        et.setUsed(false);
        tokenMapper.insert(et);

        emailService.sendActivation(u.getEmail(), et.getToken());
        return new UserBrief(u.getId(), u.getNickname(), u.getAvatar(), u.getUserType(), u.getStatus());
    }

    @Transactional
    public void activate(String token) {
        EmailToken et = tokenMapper.selectOne(Wrappers.<EmailToken>lambdaQuery()
                .eq(EmailToken::getToken, token).eq(EmailToken::getType, "ACTIVATION"));
        if (et == null || Boolean.TRUE.equals(et.getUsed()) || et.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BizException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        User u = userMapper.selectById(et.getUserId());
        if (u == null) throw new BizException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        u.setStatus("ACTIVE");
        userMapper.updateById(u);
        et.setUsed(true);
        tokenMapper.updateById(et);
    }

    public LoginResp login(LoginReq req) {
        User u = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, req.email()).isNull(User::getDeletedAt));
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash()))
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        if ("BANNED".equals(u.getStatus())) throw new BizException(ErrorCode.ACCOUNT_BANNED);
        if ("PENDING_ACTIVATION".equals(u.getStatus())) throw new BizException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        String token = jwt.generate(u.getId());
        return new LoginResp(token, jwt.getExpireSeconds(),
                new UserBrief(u.getId(), u.getNickname(), u.getAvatar(), u.getUserType(), u.getStatus()));
    }
}
