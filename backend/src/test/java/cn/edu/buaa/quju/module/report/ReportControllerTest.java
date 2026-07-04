package cn.edu.buaa.quju.module.report;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户提交举报。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {
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
    void userCanCreateReport() throws Exception {
        long userId = insertUser();
        submit(userId, body("TEAM", 100L, "垃圾广告", "反复刷屏"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from report where reporter_id = ? and target_type = 'TEAM' and target_id = 100 and status = 'PENDING'",
                Integer.class, userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidTargetTypeRejected() throws Exception {
        long userId = insertUser();
        submit(userId, body("USER", 1L, "垃圾广告", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000)); // bad_request
    }

    @Test
    void blankReasonRejected() throws Exception {
        long userId = insertUser();
        submit(userId, body("TEAM", 1L, "", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000)); // bean validation
    }

    @Test
    void duplicatePendingReportRejected() throws Exception {
        long userId = insertUser();
        submit(userId, body("ACTIVITY", 200L, "违法违规", null))
                .andExpect(jsonPath("$.code").value(0));
        submit(userId, body("ACTIVITY", 200L, "违法违规", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004)); // conflict
    }

    private Map<String, Object> body(String targetType, Long targetId, String reason, String detail) {
        Map<String, Object> m = new HashMap<>();
        m.put("targetType", targetType);
        m.put("targetId", targetId);
        m.put("reason", reason);
        if (detail != null) m.put("detail", detail);
        return m;
    }

    private org.springframework.test.web.servlet.ResultActions submit(long userId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/v1/reports")
                .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long insertUser() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) " +
                        "values (:email, 'noop', 'INDIVIDUAL', 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", "rep-" + UUID.randomUUID() + "@example.com")
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8)),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }
}
