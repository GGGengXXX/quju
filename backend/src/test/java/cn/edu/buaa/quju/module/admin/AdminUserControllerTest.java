package cn.edu.buaa.quju.module.admin;

import cn.edu.buaa.quju.common.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {
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

    private long adminId;

    @BeforeEach
    void setUp() {
        adminId = insertAdmin();
    }

    @Test
    void approveMerchantGrantsMerchantIdentity() throws Exception {
        long userId = insertUser("INDIVIDUAL");
        long profileId = insertMerchantProfile(userId, "PENDING");

        mockMvc.perform(post("/v1/admin/merchant-applications/{id}", profileId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String auditStatus = jdbcTemplate.queryForObject(
                "select audit_status from merchant_profile where id = ?", String.class, profileId);
        String userType = jdbcTemplate.queryForObject(
                "select user_type from user where id = ?", String.class, userId);
        assertThat(auditStatus).isEqualTo("APPROVED");
        assertThat(userType).isEqualTo("MERCHANT");
    }

    @Test
    void rejectMerchantWithoutReasonFails() throws Exception {
        long userId = insertUser("INDIVIDUAL");
        long profileId = insertMerchantProfile(userId, "PENDING");

        mockMvc.perform(post("/v1/admin/merchant-applications/{id}", profileId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "REJECT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6001));

        String auditStatus = jdbcTemplate.queryForObject(
                "select audit_status from merchant_profile where id = ?", String.class, profileId);
        String userType = jdbcTemplate.queryForObject(
                "select user_type from user where id = ?", String.class, userId);
        assertThat(auditStatus).isEqualTo("PENDING");
        assertThat(userType).isEqualTo("INDIVIDUAL");
    }

    @Test
    void rejectMerchantWithReasonKeepsIndividual() throws Exception {
        long userId = insertUser("INDIVIDUAL");
        long profileId = insertMerchantProfile(userId, "PENDING");

        mockMvc.perform(post("/v1/admin/merchant-applications/{id}", profileId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "REJECT", "reason", "证件模糊"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String auditStatus = jdbcTemplate.queryForObject(
                "select audit_status from merchant_profile where id = ?", String.class, profileId);
        String userType = jdbcTemplate.queryForObject(
                "select user_type from user where id = ?", String.class, userId);
        assertThat(auditStatus).isEqualTo("REJECTED");
        assertThat(userType).isEqualTo("INDIVIDUAL");
    }

    @Test
    void userDetailIncludesActivitiesAndTeams() throws Exception {
        long userId = insertUser("INDIVIDUAL");
        insertActivity(userId, "周末登山");
        insertTeam(userId, "登山小队");

        mockMvc.perform(get("/v1/admin/users/{id}", userId)
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activities[0].name").value("周末登山"))
                .andExpect(jsonPath("$.data.teams[0].name").value("登山小队"));
    }

    // ---- helpers ----

    private long insertAdmin() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into admin(username, password_hash) values (:username, 'noop')",
                new MapSqlParameterSource().addValue("username", "admin-" + UUID.randomUUID()),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private long insertUser(String userType) {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) " +
                        "values (:email, 'noop', :userType, 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", "u-" + UUID.randomUUID() + "@example.com")
                        .addValue("userType", userType)
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8)),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private long insertMerchantProfile(long userId, String auditStatus) {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into merchant_profile(user_id, merchant_name, audit_status) " +
                        "values (:userId, :name, :status)",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("name", "商家-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("status", auditStatus),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private void insertActivity(long creatorId, String name) {
        jdbcTemplate.update(
                "insert into activity(creator_id, name, status) values (?, ?, 'PUBLISHED')",
                creatorId, name);
    }

    private void insertTeam(long ownerId, String name) {
        jdbcTemplate.update(
                "insert into team(name, intro, join_type, capacity, member_count, status, owner_id) " +
                        "values (?, 'test', 'PUBLIC', 20, 1, 'ACTIVE', ?)",
                name, ownerId);
    }

    private String adminBearer() {
        return "Bearer " + jwtUtil.generateAdmin(adminId);
    }
}
