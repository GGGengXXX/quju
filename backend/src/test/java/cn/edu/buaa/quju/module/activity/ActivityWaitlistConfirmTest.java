package cn.edu.buaa.quju.module.activity;

import cn.edu.buaa.quju.common.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 候补确认流程：仅当空位释放(NOTIFIED)后才可确认，排队中(WAITING)确认应给出明确提示而非"候补确认超时"。
 * 回归 bug：刚进入候补(WAITING)点"确认候补名额"被错误地报 3006/候补确认超时。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivityWaitlistConfirmTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void waitingUserSeesWaitingStatusAndCannotConfirmYet() throws Exception {
        long owner = insertUser();
        long activityId = insertFullCapacityActivity(owner);
        long userA = insertUser();
        long userB = insertUser();

        // A 报名占满唯一名额
        signup(activityId, userA).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REGISTERED"));
        // B 报名进入候补(WAITING)
        signup(activityId, userB).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("WAITLISTED"));

        // 详情向 B 暴露原始候补状态 WAITING
        detail(activityId, userB).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mySignupStatus").value("WAITLISTED"))
                .andExpect(jsonPath("$.data.myWaitlistStatus").value("WAITING"));

        // WAITING 状态点"确认候补名额"：仍失败，但给出明确"暂无空位"提示（而非误导性的超时）
        confirm(activityId, userB).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3006))
                .andExpect(jsonPath("$.message").value("当前暂无空位，轮到你时会通知你确认"));
    }

    @Test
    void notifiedUserCanConfirmAndGetsPromoted() throws Exception {
        long owner = insertUser();
        long activityId = insertFullCapacityActivity(owner);
        long userA = insertUser();
        long userB = insertUser();

        signup(activityId, userA).andExpect(jsonPath("$.data.status").value("REGISTERED"));
        signup(activityId, userB).andExpect(jsonPath("$.data.status").value("WAITLISTED"));

        // A 取消 → 释放名额 → B 被通知(NOTIFIED)
        mockMvc.perform(delete("/v1/activities/" + activityId + "/signup")
                .header("Authorization", "Bearer " + jwtUtil.generate(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        detail(activityId, userB).andExpect(jsonPath("$.data.myWaitlistStatus").value("NOTIFIED"));

        // NOTIFIED 状态确认成功 → 转正
        confirm(activityId, userB).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        detail(activityId, userB).andExpect(jsonPath("$.data.mySignupStatus").value("REGISTERED"))
                .andExpect(jsonPath("$.data.myWaitlistStatus").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions signup(long activityId, long userId) throws Exception {
        return mockMvc.perform(post("/v1/activities/" + activityId + "/signup")
                .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("safetyConfirmed", true))));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(long activityId, long userId) throws Exception {
        return mockMvc.perform(post("/v1/activities/" + activityId + "/waitlist/confirm")
                .header("Authorization", "Bearer " + jwtUtil.generate(userId)));
    }

    private org.springframework.test.web.servlet.ResultActions detail(long activityId, long userId) throws Exception {
        return mockMvc.perform(get("/v1/activities/" + activityId)
                .header("Authorization", "Bearer " + jwtUtil.generate(userId)));
    }

    private long insertFullCapacityActivity(long creatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into activity(creator_id, name, category, status, capacity, start_time, signup_deadline) " +
                        "values (:creatorId, :name, 'OTHER', 'PUBLISHED', 1, :start, :deadline)",
                new MapSqlParameterSource()
                        .addValue("creatorId", creatorId)
                        .addValue("name", "候补测试活动-" + UUID.randomUUID())
                        .addValue("start", LocalDateTime.now().plusDays(3))
                        .addValue("deadline", LocalDateTime.now().plusDays(1)),
                keyHolder,
                new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    private long insertUser() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation, birthday) " +
                        "values (:email, 'noop', 'INDIVIDUAL', 'ACTIVE', :nickname, 'UNKNOWN', 100, '2000-01-01')",
                new MapSqlParameterSource()
                        .addValue("email", "wl-" + UUID.randomUUID() + "@example.com")
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8)),
                keyHolder,
                new String[]{"id"});
        return keyHolder.getKey().longValue();
    }
}
