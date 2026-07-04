package cn.edu.buaa.quju.module.activity;

import cn.edu.buaa.quju.common.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核结果可见性：活动详情 latestAudit（仅发起人本人可见）+ 管理员审核流水时间线端点。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActivityAuditVisibilityTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void ownerSeesLatestAudit() throws Exception {
        long ownerId = insertUser();
        long activityId = insertActivity(ownerId, "PUBLISHED");
        insertAudit(activityId, "AI", "PASSED", "正常活动", null);

        mockMvc.perform(get("/v1/activities/{id}", activityId)
                        .header("Authorization", "Bearer " + jwtUtil.generate(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.latestAudit.result").value("PASSED"))
                .andExpect(jsonPath("$.data.latestAudit.auditType").value("AI"))
                .andExpect(jsonPath("$.data.latestAudit.reason").value("正常活动"));
    }

    @Test
    void ownerSeesMostRecentAuditOnly() throws Exception {
        long ownerId = insertUser();
        long activityId = insertActivity(ownerId, "PUBLISHED");
        insertAudit(activityId, "AI", "TO_MANUAL", "灰区活动", null);
        insertAudit(activityId, "MANUAL", "PASSED", "人工放行", 1L);

        mockMvc.perform(get("/v1/activities/{id}", activityId)
                        .header("Authorization", "Bearer " + jwtUtil.generate(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestAudit.result").value("PASSED"))
                .andExpect(jsonPath("$.data.latestAudit.auditType").value("MANUAL"));
    }

    @Test
    void nonOwnerDoesNotSeeLatestAudit() throws Exception {
        long ownerId = insertUser();
        long viewerId = insertUser();
        long activityId = insertActivity(ownerId, "PUBLISHED");
        insertAudit(activityId, "AI", "PASSED", "正常活动", null);

        mockMvc.perform(get("/v1/activities/{id}", activityId)
                        .header("Authorization", "Bearer " + jwtUtil.generate(viewerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.latestAudit").doesNotExist());
    }

    @Test
    void adminAuditLogsReturnedInChronologicalOrder() throws Exception {
        long adminId = insertAdmin();
        long ownerId = insertUser();
        long activityId = insertActivity(ownerId, "PENDING_REVIEW");
        insertAudit(activityId, "AI", "TO_MANUAL", "AI 无法判断", null);
        insertAudit(activityId, "MANUAL", "REJECTED", "人工驳回", adminId);

        mockMvc.perform(get("/v1/admin/activities/{id}/audit-logs", activityId)
                        .header("Authorization", "Bearer " + jwtUtil.generateAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].auditType").value("AI"))
                .andExpect(jsonPath("$.data[0].result").value("TO_MANUAL"))
                .andExpect(jsonPath("$.data[1].auditType").value("MANUAL"))
                .andExpect(jsonPath("$.data[1].result").value("REJECTED"))
                .andExpect(jsonPath("$.data[1].auditorAdminId").value(adminId));
    }

    // ---- helpers ----

    private void insertAudit(long activityId, String auditType, String result, String reason, Long adminId) {
        namedJdbcTemplate.update(
                "insert into activity_audit_log(activity_id, audit_type, result, reason, auditor_admin_id) " +
                        "values (:activityId, :auditType, :result, :reason, :adminId)",
                new MapSqlParameterSource()
                        .addValue("activityId", activityId)
                        .addValue("auditType", auditType)
                        .addValue("result", result)
                        .addValue("reason", reason)
                        .addValue("adminId", adminId));
    }

    private long insertActivity(long creatorId, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into activity(creator_id, name, status) values (:creator, :name, :status)",
                new MapSqlParameterSource()
                        .addValue("creator", creatorId)
                        .addValue("name", "活动-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("status", status),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private long insertUser() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) " +
                        "values (:email, 'noop', 'INDIVIDUAL', 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", "audit-" + UUID.randomUUID() + "@example.com")
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8)),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private long insertAdmin() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into admin(username, password_hash) values (:username, 'noop')",
                new MapSqlParameterSource().addValue("username", "adm-" + UUID.randomUUID().toString().substring(0, 8)),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }
}
