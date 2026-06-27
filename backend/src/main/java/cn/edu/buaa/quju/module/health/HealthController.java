package cn.edu.buaa.quju.module.health;

import cn.edu.buaa.quju.common.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 最小冒烟端点：验证 nginx → /v1 反代 → 后端 → MySQL 整条链路。
 * 正式模块按 contracts/openapi.yaml + backend/AGENTS.md 实现。
 */
@RestController
@RequestMapping("/v1")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "quju-backend");
        data.put("status", "UP");
        data.put("time", OffsetDateTime.now().toString());
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            data.put("db", (one != null && one == 1) ? "UP" : "DOWN");
        } catch (Exception e) {
            data.put("db", "DOWN: " + e.getClass().getSimpleName());
        }
        return R.ok(data);
    }
}
