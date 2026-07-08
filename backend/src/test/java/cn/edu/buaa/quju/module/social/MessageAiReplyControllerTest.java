package cn.edu.buaa.quju.module.social;

import cn.edu.buaa.quju.common.JwtUtil;
import cn.edu.buaa.quju.module.social.service.ChatAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessageAiReplyControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @MockBean
    private ChatAiService chatAiService;

    @Test
    void friendConversationCanGenerateAiReplyDraft() throws Exception {
        long userId = insertUser("我");
        long peerId = insertUser("小王");
        insertFriendship(userId, peerId);
        insertFriendship(peerId, userId);
        insertMessage("FRIEND", userId, peerId, null, "TEXT", "周六下午去吗？");
        insertMessage("FRIEND", peerId, userId, null, "TEXT", "可以，我两点到。");
        namedJdbcTemplate.update(
                "update user set privacy_settings = :settings where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("settings", "{\"showActivities\":true,\"showTeams\":true,\"aiSettings\":{\"systemPrompt\":\"礼貌一点\"}}"));

        when(chatAiService.generateReply(eq("礼貌一点"), contains("周六下午去吗"))).thenReturn("好的，那我周六两点见。");

        mockMvc.perform(post("/v1/messages/ai-reply")
                        .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scope", "FRIEND", "peerId", peerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.suggestion").value("好的，那我周六两点见。"))
                .andExpect(jsonPath("$.data.contextCount").value(2));

        verify(chatAiService).generateReply(eq("礼貌一点"), contains("小王"));
    }

    @Test
    void nonMemberCannotGenerateTeamAiReply() throws Exception {
        long userId = insertUser("我");
        long ownerId = insertUser("队长");
        long teamId = insertTeam(ownerId, "周末骑行队");

        mockMvc.perform(post("/v1/messages/ai-reply")
                        .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scope", "TEAM", "peerId", teamId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    private long insertUser(String nickname) {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) " +
                        "values (:email, 'noop', 'INDIVIDUAL', 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", "u-" + UUID.randomUUID() + "@example.com")
                        .addValue("nickname", nickname),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }

    private void insertFriendship(long ownerId, long friendId) {
        namedJdbcTemplate.update(
                "insert into friendship(owner_id, friend_id) values (:ownerId, :friendId)",
                new MapSqlParameterSource().addValue("ownerId", ownerId).addValue("friendId", friendId));
    }

    private void insertMessage(String scope, long senderId, Long receiverId, Long teamId, String contentType, String content) {
        namedJdbcTemplate.update(
                "insert into message(scope, sender_id, receiver_id, team_id, content_type, content, is_read, is_recalled) " +
                        "values (:scope, :senderId, :receiverId, :teamId, :contentType, :content, 0, 0)",
                new MapSqlParameterSource()
                        .addValue("scope", scope)
                        .addValue("senderId", senderId)
                        .addValue("receiverId", receiverId)
                        .addValue("teamId", teamId)
                        .addValue("contentType", contentType)
                        .addValue("content", content));
    }

    private long insertTeam(long ownerId, String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into team(name, owner_id, join_type, member_count, status) values (:name, :ownerId, 'PUBLIC', 1, 'ACTIVE')",
                new MapSqlParameterSource().addValue("name", name).addValue("ownerId", ownerId),
                kh, new String[]{"id"});
        long teamId = kh.getKey().longValue();
        namedJdbcTemplate.update(
                "insert into team_member(team_id, user_id, role, points) values (:teamId, :userId, 'OWNER', 0)",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", ownerId));
        return teamId;
    }
}
