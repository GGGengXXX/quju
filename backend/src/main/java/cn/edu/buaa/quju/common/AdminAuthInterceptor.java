package cn.edu.buaa.quju.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 管理员鉴权拦截器：解析 Bearer token，subject 以 "admin:" 开头则注入 AdminContext。 */
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwt;
    public AdminAuthInterceptor(JwtUtil jwt) { this.jwt = jwt; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            Long adminId = jwt.parseAdmin(auth.substring(7));
            if (adminId != null) AdminContext.set(adminId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        AdminContext.clear();
    }
}
