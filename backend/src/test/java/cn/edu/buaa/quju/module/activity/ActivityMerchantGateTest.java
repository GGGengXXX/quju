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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 未过审商家禁止发起活动的门槛校验。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivityMerchantGateTest {
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
    void pendingMerchantCannotCreateActivity() throws Exception {
        long userId = insertMerchant("PENDING");
        createActivity(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002)); // forbidden
    }

    @Test
    void rejectedMerchantCannotCreateActivity() throws Exception {
        long userId = insertMerchant("REJECTED");
        createActivity(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void approvedMerchantCanCreateActivity() throws Exception {
        long userId = insertMerchant("APPROVED");
        createActivity(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void individualCanCreateActivity() throws Exception {
        long userId = insertUser("INDIVIDUAL");
        createActivity(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions createActivity(long userId) throws Exception {
        return mockMvc.perform(post("/v1/activities")
                .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "测试活动-" + UUID.randomUUID()))));
    }

    private long insertMerchant(String auditStatus) {
        long userId = insertUser("MERCHANT");
        jdbcTemplate.update(
                "insert into merchant_profile(user_id, merchant_name, license_url, audit_status) values (?, ?, ?, ?)",
                userId, "测试商家", "https://oss/license.jpg", auditStatus);
        return userId;
    }

    private long insertUser(String userType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) " +
                        "values (:email, 'noop', :userType, 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", "gate-" + UUID.randomUUID() + "@example.com")
                        .addValue("userType", userType)
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8)),
                keyHolder,
                new String[]{"id"});
        return keyHolder.getKey().longValue();
    }
}
