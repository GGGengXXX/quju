package cn.edu.buaa.quju.module.user;

import cn.edu.buaa.quju.common.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserProfileAiSettingsTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @Test
    void updateProfilePersistsAiSettingsAndPrivacyTogether() throws Exception {
        long userId = insertUser();

        mockMvc.perform(put("/v1/users/me")
                        .header("Authorization", "Bearer " + jwtUtil.generate(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "新昵称",
                                "interestTags", List.of("徒步", "桌游"),
                                "privacySettings", Map.of("showActivities", false, "showTeams", true),
                                "aiSettings", Map.of("systemPrompt", "回复尽量简短，友好一点")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.aiSettings.systemPrompt").value("回复尽量简短，友好一点"))
                .andExpect(jsonPath("$.data.privacySettings.showActivities").value(false))
                .andExpect(jsonPath("$.data.privacySettings.showTeams").value(true));

        String settingsJson = namedJdbcTemplate.getJdbcTemplate().queryForObject(
                "select privacy_settings from user where id = ?", String.class, userId);
        var settingsNode = objectMapper.readTree(settingsJson);
        assertThat(settingsNode.path("showActivities").asBoolean()).isFalse();
        assertThat(settingsNode.path("showTeams").asBoolean()).isTrue();
        assertThat(settingsNode.path("aiSettings").path("systemPrompt").asText()).isEqualTo("回复尽量简短，友好一点");

        mockMvc.perform(get("/v1/users/me")
                        .header("Authorization", "Bearer " + jwtUtil.generate(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSettings.systemPrompt").value("回复尽量简短，友好一点"))
                .andExpect(jsonPath("$.data.privacySettings.showActivities").value(false));
    }

    private long insertUser() {
        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbcTemplate.update(
                "insert into user(email, password_hash, user_type, status, nickname, gender, reputation) " +
                        "values (:email, 'noop', 'INDIVIDUAL', 'ACTIVE', :nickname, 'UNKNOWN', 100)",
                new MapSqlParameterSource()
                        .addValue("email", "profile-" + UUID.randomUUID() + "@example.com")
                        .addValue("nickname", "u" + UUID.randomUUID().toString().substring(0, 8)),
                kh, new String[]{"id"});
        return kh.getKey().longValue();
    }
}
