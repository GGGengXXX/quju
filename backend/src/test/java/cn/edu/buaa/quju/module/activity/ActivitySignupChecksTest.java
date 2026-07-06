package cn.edu.buaa.quju.module.activity;

import cn.edu.buaa.quju.common.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 报名资质校验：商家账号无生日/年龄概念，报名应跳过生日校验；个人用户仍需完善生日。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivitySignupChecksTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void merchantWithoutBirthdayCanSignup() throws Exception {
        long owner = insertUser("INDIVIDUAL", "2000-01-01");
        long activityId = insertActivity(owner);
        long merchant = insertUser("MERCHANT", null); // 商家无生日

        signup(activityId, merchant)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REGISTERED"));
    }

    @Test
    void individualWithoutBirthdayIsRejected() throws Exception {
        long owner = insertUser("INDIVIDUAL", "2000-01-01");
        long activityId = insertActivity(owner);
        long individual = insertUser("INDIVIDUAL", null); // 个人无生日

        signup(activityId, individual)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005)); // signup_check_failed
    }

    private org.springframework.test.web.servlet.ResultActions signup(long activityId, long userId) throws Exception {
        return mockMvc.perform(post("/v1/activities/" + activityId + "/signup")
                .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"safetyConfirmed\":true}"));
    }

    private long insertActivity(long creatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into activity(creator_id, name, category, status, capacity, start_time, signup_deadline) " +
                        "values (:creatorId, :name, 'OTHER', 'PUBLISHED', 20, :start, :deadline)",
                new MapSqlParameterSource()
                        .addValue("creatorId", creatorId)
                        .addValue("name", "报名校验测试-" + UUID.randomUUID())
                        .addValue("start", LocalDateTime.now().plusDays(3))
                        .addValue("deadline", LocalDateTime.now().plusDays(1)),
                keyHolder,
                new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    private long insertUser(String userType, String birthday) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation, birthday) " +
                        "values (:email, 'noop', :userType, 'ACTIVE', :nickname, 'UNKNOWN', 100, :birthday)",
                new MapSqlParameterSource()
                        .addValue("email", "chk-" + UUID.randomUUID() + "@example.com")
                        .addValue("userType", userType)
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("birthday", birthday),
                keyHolder,
                new String[]{"id"});
        return keyHolder.getKey().longValue();
    }
}
