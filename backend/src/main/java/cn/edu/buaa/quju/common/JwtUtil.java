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
    private final SecretKey key;
    private final long expireMs;

    public JwtUtil(@Value("${quju.jwt.secret}") String secret,
                   @Value("${quju.jwt.expire-seconds:604800}") long expireSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) { // HS256 需 >= 32 字节
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) padded[i] = 'q';
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMs = expireSeconds * 1000;
    }

    public String generate(long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    /** 解析并校验，返回 userId；无效返回 null。 */
    public Long parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return Long.valueOf(jws.getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getExpireSeconds() { return expireMs / 1000; }
}
