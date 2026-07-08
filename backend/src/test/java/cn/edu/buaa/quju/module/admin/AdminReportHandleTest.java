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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理员处理举报（含联动下架/停用目标）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReportHandleTest {
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
    private long reporterId;

    @BeforeEach
    void setUp() {
        adminId = insertAdmin();
        reporterId = insertUser();
    }

    @Test
    void dismissReport() throws Exception {
        long reportId = insertReport("TEAM", 999L, "PENDING");
        handle(reportId, Map.of("action", "DISMISS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(reportStatus(reportId)).isEqualTo("DISMISSED");
        assertThat(handlerAdminId(reportId)).isEqualTo(adminId);
    }

    @Test
    void takedownSuspendsTeamAndResolvesReport() throws Exception {
        long teamId = insertTeam();
        long reportId = insertReport("TEAM", teamId, "PENDING");
        handle(reportId, Map.of("action", "TAKEDOWN", "reason", "队伍传播违规内容"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(reportStatus(reportId)).isEqualTo("HANDLED");
        String teamStatus = jdbcTemplate.queryForObject("select status from team where id = ?", String.class, teamId);
        assertThat(teamStatus).isEqualTo("SUSPENDED");
    }

    @Test
    void takedownTakesDownActivity() throws Exception {
        long activityId = insertActivity();
        long reportId = insertReport("ACTIVITY", activityId, "PENDING");
        handle(reportId, Map.of("action", "TAKEDOWN", "reason", "虚假活动"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(reportStatus(reportId)).isEqualTo("HANDLED");
        String status = jdbcTemplate.queryForObject("select status from activity where id = ?", String.class, activityId);
        assertThat(status).isEqualTo("TAKEN_DOWN");
    }

    @Test
    void takedownWithoutReasonFails() throws Exception {
        long teamId = insertTeam();
        long reportId = insertReport("TEAM", teamId, "PENDING");
        handle(reportId, Map.of("action", "TAKEDOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6002)); // takedown_reason_required
        assertThat(reportStatus(reportId)).isEqualTo("PENDING");
        String teamStatus = jdbcTemplate.queryForObject("select status from team where id = ?", String.class, teamId);
        assertThat(teamStatus).isEqualTo("ACTIVE");
    }

    @Test
    void unknownReportNotFound() throws Exception {
        handle(99999999L, Map.of("action", "DISMISS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003)); // not_found
    }

    @Test
    void alreadyHandledConflict() throws Exception {
        long reportId = insertReport("TEAM", 999L, "HANDLED");
        handle(reportId, Map.of("action", "DISMISS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004)); // conflict
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions handle(long reportId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/v1/admin/reports/{id}/handle", reportId)
                .header("Authorization", "Bearer " + jwtUtil.generateAdmin(adminId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String reportStatus(long reportId) {
        return jdbcTemplate.queryForObject("select status from report where id = ?", String.class, reportId);
    }

    private long handlerAdminId(long reportId) {
        return jdbcTemplate.queryForObject("select handler_admin_id from report where id = ?", Long.class, reportId);
    }

    private long insertReport(String targetType, long targetId, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into report(reporter_id, target_type, target_id, reason, status) " +
                        "values (:reporter, :type, :target, :reason, :status)",
                new MapSqlParameterSource()
                        .addValue("reporter", reporterId)
                        .addValue("type", targetType)
                        .addValue("target", targetId)
                        .addValue("reason", "测试原因")
                        .addValue("status", status),
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

    private long insertTeam() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into team(name, intro, join_type, capacity, member_count, status, owner_id) " +
                        "values (:name, 'test', 'PUBLIC', 20, 1, 'ACTIVE', :owner)",
                new MapSqlParameterSource()
                        .addValue("name", "队-" + UUID.randomUUID().toString().substring(0, 8))
                        .addValue("owner", reporterId),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private long insertActivity() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into activity(creator_id, name, status) values (:creator, :name, 'PUBLISHED')",
                new MapSqlParameterSource()
                        .addValue("creator", reporterId)
                        .addValue("name", "活动-" + UUID.randomUUID().toString().substring(0, 8)),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }
}
