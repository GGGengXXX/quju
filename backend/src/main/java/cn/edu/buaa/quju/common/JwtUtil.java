package cn.edu.buaa.quju.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private static final String ADMIN_PREFIX = "admin:";
    private final SecretKey key;
    private final long expireMs;

    public JwtUtil(@Value("${quju.jwt.secret}") String secret,
                   @Value("${quju.jwt.expire-seconds:604800}") long expireSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) padded[i] = 'q';
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMs = expireSeconds * 1000;
    }

    /** 生成普通用户 token，subject = userId */
    public String generate(long userId) {
        return build(String.valueOf(userId));
    }

    /** 生成管理员 token，subject = "admin:<adminId>" */
    public String generateAdmin(long adminId) {
        return build(ADMIN_PREFIX + adminId);
    }

    /** 解析用户 token，返回 userId；非用户 token 返回 null */
    public Long parse(String token) {
        String subject = parseSubject(token);
        if (subject == null || subject.startsWith(ADMIN_PREFIX)) return null;
        try { return Long.valueOf(subject); } catch (NumberFormatException e) { return null; }
    }

    /** 解析管理员 token，返回 adminId；非管理员 token 返回 null */
    public Long parseAdmin(String token) {
        String subject = parseSubject(token);
        if (subject == null || !subject.startsWith(ADMIN_PREFIX)) return null;
        try { return Long.valueOf(subject.substring(ADMIN_PREFIX.length())); } catch (NumberFormatException e) { return null; }
    }

    public long getExpireSeconds() { return expireMs / 1000; }

    private String build(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    private String parseSubject(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return jws.getPayload().getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
