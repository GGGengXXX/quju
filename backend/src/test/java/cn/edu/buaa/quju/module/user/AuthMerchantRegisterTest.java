package cn.edu.buaa.quju.module.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthMerchantRegisterTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void merchantRegisterCreatesPendingMerchantProfile() throws Exception {
        String email = "m-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123",
                                "userType", "MERCHANT",
                                "licenseUrl", "https://oss/license.jpg",
                                "merchantName", "趣聚测试商家"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Long userId = jdbcTemplate.queryForObject(
                "select id from user where email = ?", Long.class, email);
        String userType = jdbcTemplate.queryForObject(
                "select user_type from user where id = ?", String.class, userId);
        String userStatus = jdbcTemplate.queryForObject(
                "select status from user where id = ?", String.class, userId);
        assertThat(userType).isEqualTo("MERCHANT");
        assertThat(userStatus).isEqualTo("PENDING_ACTIVATION");

        Map<String, Object> mp = jdbcTemplate.queryForMap(
                "select merchant_name, license_url, audit_status from merchant_profile where user_id = ?", userId);
        assertThat(mp.get("merchant_name")).isEqualTo("趣聚测试商家");
        assertThat(mp.get("license_url")).isEqualTo("https://oss/license.jpg");
        assertThat(mp.get("audit_status")).isEqualTo("PENDING");
    }

    @Test
    void merchantRegisterWithoutNameFails() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "m-" + UUID.randomUUID() + "@example.com");
        body.put("password", "password123");
        body.put("userType", "MERCHANT");
        body.put("licenseUrl", "https://oss/license.jpg");
        // 缺 merchantName

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2006));
    }

    @Test
    void merchantRegisterWithoutLicenseFails() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "m-" + UUID.randomUUID() + "@example.com");
        body.put("password", "password123");
        body.put("userType", "MERCHANT");
        body.put("merchantName", "趣聚测试商家");
        // 缺 licenseUrl

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005));
    }

    @Test
    void individualRegisterDoesNotCreateMerchantProfile() throws Exception {
        String email = "i-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123",
                                "userType", "INDIVIDUAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Long userId = jdbcTemplate.queryForObject(
                "select id from user where email = ?", Long.class, email);
        Integer mpCount = jdbcTemplate.queryForObject(
                "select count(*) from merchant_profile where user_id = ?", Integer.class, userId);
        assertThat(mpCount).isEqualTo(0);
    }
}
