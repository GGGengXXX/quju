package cn.edu.buaa.quju.module.team;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerTest {
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

    @BeforeEach
    void cleanTeamTables() {
        jdbcTemplate.update("delete from team_vote_record");
        jdbcTemplate.update("delete from team_vote");
        jdbcTemplate.update("delete from team_announcement");
        jdbcTemplate.update("delete from team_file");
        jdbcTemplate.update("delete from team_album_photo");
        jdbcTemplate.update("delete from team_moment");
        jdbcTemplate.update("delete from team_points_log");
        jdbcTemplate.update("delete from team_join_request");
        jdbcTemplate.update("delete from team_member");
        jdbcTemplate.update("delete from team_tag");
        jdbcTemplate.update("delete from team");
        jdbcTemplate.update("delete from user_block");
        jdbcTemplate.update("delete from activity where team_id is not null");
        jdbcTemplate.update("delete from user");
    }

    @Test
    void createTeamCreatesOwnerMemberAndTags() throws Exception {
        long ownerId = insertUser("owner");

        mockMvc.perform(post("/v1/teams")
                        .header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "周末爬山队",
                                "intro", "一起约山野",
                                "tags", List.of("户外", "登山"),
                                "joinType", "PUBLIC",
                                "capacity", 50
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("周末爬山队"))
                .andExpect(jsonPath("$.data.owner.id").value(ownerId));

        Integer memberCount = jdbcTemplate.queryForObject("select count(*) from team_member where user_id = ? and role = 'OWNER'", Integer.class, ownerId);
        Integer tagCount = jdbcTemplate.queryForObject("select count(*) from team_tag", Integer.class);
        assertThat(memberCount).isEqualTo(1);
        assertThat(tagCount).isEqualTo(2);
    }

    @Test
    void publicTeamJoinAddsMemberImmediately() throws Exception {
        long ownerId = insertUser("owner");
        long memberId = insertUser("member");
        long teamId = insertTeam(ownerId, "PUBLIC");

        mockMvc.perform(post("/v1/teams/{id}/join", teamId)
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("JOINED"));

        Integer memberCount = jdbcTemplate.queryForObject("select count(*) from team_member where team_id = ?", Integer.class, teamId);
        Integer teamCount = jdbcTemplate.queryForObject("select member_count from team where id = ?", Integer.class, teamId);
        assertThat(memberCount).isEqualTo(2);
        assertThat(teamCount).isEqualTo(2);
    }

    @Test
    void approvalJoinCanBeApprovedByOwner() throws Exception {
        long ownerId = insertUser("owner");
        long applicantId = insertUser("applicant");
        long teamId = insertTeam(ownerId, "APPROVAL");

        mockMvc.perform(post("/v1/teams/{id}/join", teamId)
                        .header("Authorization", bearer(applicantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        Long requestId = jdbcTemplate.queryForObject("select id from team_join_request where team_id = ? and user_id = ?", Long.class, teamId, applicantId);
        assertThat(requestId).isNotNull();

        mockMvc.perform(post("/v1/teams/{id}/join-requests/{reqId}", teamId, requestId)
                        .header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("action", "APPROVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer joined = jdbcTemplate.queryForObject("select count(*) from team_member where team_id = ? and user_id = ?", Integer.class, teamId, applicantId);
        String status = jdbcTemplate.queryForObject("select status from team_join_request where id = ?", String.class, requestId);
        assertThat(joined).isEqualTo(1);
        assertThat(status).isEqualTo("APPROVED");
    }

    @Test
    void nonManagerCannotPublishAnnouncement() throws Exception {
        long ownerId = insertUser("owner");
        long memberId = insertUser("member");
        long teamId = insertTeam(ownerId, "PUBLIC");
        insertMember(teamId, memberId, "MEMBER");
        jdbcTemplate.update("update team set member_count = 2 where id = ?", teamId);

        mockMvc.perform(post("/v1/teams/{id}/announcements", teamId)
                        .header("Authorization", bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "集合时间更新"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5004))
                .andExpect(jsonPath("$.message").value("no_team_permission"));
    }

    private long insertUser(String prefix) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) values (:email, :passwordHash, 'INDIVIDUAL', 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", prefix + "-" + UUID.randomUUID() + "@example.com")
                        .addValue("passwordHash", "noop")
                        .addValue("nickname", prefix + UUID.randomUUID().toString().substring(0, 8)),
                keyHolder,
                new String[]{"id"}
        );
        return keyHolder.getKey().longValue();
    }

    private long insertTeam(long ownerId, String joinType) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into team(name, intro, join_type, capacity, member_count, status, owner_id) values (:name, :intro, :joinType, 20, 1, 'ACTIVE', :ownerId)",
                new MapSqlParameterSource()
                        .addValue("name", "team-" + UUID.randomUUID())
                        .addValue("intro", "test")
                        .addValue("joinType", joinType)
                        .addValue("ownerId", ownerId),
                keyHolder,
                new String[]{"id"}
        );
        long teamId = keyHolder.getKey().longValue();
        insertMember(teamId, ownerId, "OWNER");
        return teamId;
    }

    private void insertMember(long teamId, long userId, String role) {
        jdbcTemplate.update("insert into team_member(team_id, user_id, role, points) values (?, ?, ?, 0)", teamId, userId, role);
    }

    private String bearer(long userId) {
        return "Bearer " + jwtUtil.generate(userId);
    }
}
