package cn.edu.buaa.quju.module.user.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.common.JwtUtil;
import cn.edu.buaa.quju.module.user.dto.UserDtos.LoginReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.LoginResp;
import cn.edu.buaa.quju.module.user.dto.UserDtos.PasswordResetReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.PasswordResetRequestReq;
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

    public AuthService(UserMapper userMapper, EmailTokenMapper tokenMapper,
                       EmailService emailService, JwtUtil jwt) {
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
        u.setAccountId(generateAccountId());
        userMapper.insert(u);

        String token = createToken(u.getId(), "ACTIVATION", 24);
        emailService.sendActivation(u.getEmail(), token);
        return toUserBrief(u);
    }

    @Transactional
    public void activate(String token) {
        EmailToken et = requireValidToken(token, "ACTIVATION");
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
        return new LoginResp(token, jwt.getExpireSeconds(), toUserBrief(u));
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequestReq req) {
        User u = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, req.email()).isNull(User::getDeletedAt));
        // 不泄露邮箱是否存在：始终返回成功
        if (u == null || !"ACTIVE".equals(u.getStatus())) return;
        String token = createToken(u.getId(), "RESET_PASSWORD", 1);
        emailService.sendPasswordReset(u.getEmail(), token);
    }

    @Transactional
    public void resetPassword(PasswordResetReq req) {
        EmailToken et = requireValidToken(req.token(), "RESET_PASSWORD");
        User u = userMapper.selectById(et.getUserId());
        if (u == null) throw new BizException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        u.setPasswordHash(encoder.encode(req.newPassword()));
        userMapper.updateById(u);
        et.setUsed(true);
        tokenMapper.updateById(et);
    }

    // ---- 工具方法 ----

    private String generateAccountId() {
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            int len = rng.nextInt(6, 9); // 6-8 位
            StringBuilder sb = new StringBuilder(len);
            sb.append(rng.nextInt(1, 10)); // 首位不为0
            for (int j = 1; j < len; j++) sb.append(rng.nextInt(10));
            String candidate = sb.toString();
            Long count = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getAccountId, candidate));
            if (count == null || count == 0) return candidate;
        }
        // fallback: 用时间戳后8位
        return String.valueOf(System.currentTimeMillis() % 100000000L);
    }

    private String createToken(Long userId, String type, int expiryHours) {
        EmailToken et = new EmailToken();
        et.setUserId(userId);
        et.setToken(UUID.randomUUID().toString().replace("-", ""));
        et.setType(type);
        et.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        et.setUsed(false);
        tokenMapper.insert(et);
        return et.getToken();
    }

    private EmailToken requireValidToken(String token, String type) {
        EmailToken et = tokenMapper.selectOne(Wrappers.<EmailToken>lambdaQuery()
                .eq(EmailToken::getToken, token).eq(EmailToken::getType, type));
        if (et == null || Boolean.TRUE.equals(et.getUsed()) || et.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BizException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        return et;
    }

    private UserBrief toUserBrief(User u) {
        return new UserBrief(u.getId(), u.getAccountId(), u.getNickname(), u.getAvatar(), u.getUserType(), u.getStatus());
    }
}
