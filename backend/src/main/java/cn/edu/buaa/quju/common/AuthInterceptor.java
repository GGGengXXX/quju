package cn.edu.buaa.quju.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 有 Bearer Token 就解析并放入 UserContext；是否强制登录由各接口 UserContext.require() 决定。 */
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwt;
    public AuthInterceptor(JwtUtil jwt) { this.jwt = jwt; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long uid = jwt.parse(auth.substring(7));
            if (uid != null) UserContext.set(uid);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        UserContext.clear();
    }
}
