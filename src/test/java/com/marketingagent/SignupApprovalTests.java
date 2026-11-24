package com.marketingagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 가입 승인이 켜진 운영 설정에서, 가입 → 승인 대기 → 최고 관리자 승인 → 로그인 흐름을 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:signup-approval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.signup-approval-required=true",
        "app.admin-password=super-admin-pass-1234"
})
@AutoConfigureMockMvc
class SignupApprovalTests {

    private static final String PASSWORD = "pw-12345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void newSignupWaitsForSuperAdminApproval() throws Exception {
        // 가입하면 토큰 없이 승인 대기
        JsonNode signup = data(json(post("/api/auth/register"),
                "{\"email\":\"newbie@approval.test\",\"password\":\"" + PASSWORD + "\",\"companyName\":\"신규업체\"}")
                .andExpect(status().isCreated()));
        assertThat(signup.get("status").asText()).isEqualTo("PENDING");
        assertThat(signup.get("token").isNull()).isTrue();

        // 승인 전에는 로그인이 막히고 이유를 알려 준다
        String pendingMessage = message(login("newbie@approval.test", PASSWORD).andExpect(status().isForbidden()));
        assertThat(pendingMessage).contains("승인");
        // 비밀번호가 틀리면 상태를 알려 주지 않는다
        login("newbie@approval.test", "wrong-password").andExpect(status().isUnauthorized());

        // 최고 관리자(admin)가 목록에서 보고 승인한다
        JsonNode admin = data(login("admin@marketing.local", "super-admin-pass-1234").andExpect(status().isOk()));
        assertThat(admin.get("superAdmin").asBoolean()).isTrue();
        String adminToken = "Bearer " + admin.get("token").asText();

        JsonNode list = data(mockMvc.perform(get("/api/super-admin/signups").header("Authorization", adminToken))
                .andExpect(status().isOk()));
        JsonNode newbie = find(list, "newbie@approval.test");
        assertThat(newbie.get("status").asText()).isEqualTo("PENDING");
        assertThat(newbie.get("companyName").asText()).isEqualTo("신규업체");
        assertThat(find(list, "admin@marketing.local")).as("최고 관리자는 목록에 없다").isNull();

        mockMvc.perform(post("/api/super-admin/signups/" + newbie.get("id").asLong() + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // 승인 후 로그인된다. 일반 업체 관리자는 승인 화면을 쓸 수 없다
        JsonNode approved = data(login("newbie@approval.test", PASSWORD).andExpect(status().isOk()));
        assertThat(approved.get("superAdmin").asBoolean()).isFalse();
        String userToken = "Bearer " + approved.get("token").asText();
        mockMvc.perform(get("/api/super-admin/signups").header("Authorization", userToken))
                .andExpect(status().isForbidden());

        // 이용 중지하면 이미 받은 토큰도 더는 통하지 않는다
        mockMvc.perform(post("/api/super-admin/signups/" + newbie.get("id").asLong() + "/reject")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ads/accounts").header("Authorization", userToken))
                .andExpect(status().is(org.hamcrest.Matchers.oneOf(401, 403)));
        login("newbie@approval.test", PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void superAdminAccountCannotBeRejected() throws Exception {
        String adminToken = "Bearer " + data(login("admin@marketing.local", "super-admin-pass-1234")
                .andExpect(status().isOk())).get("token").asText();
        long adminId = userRepository.findByEmail("admin@marketing.local").orElseThrow().getId();
        mockMvc.perform(post("/api/super-admin/signups/" + adminId + "/reject").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    private ResultActions login(String email, String password) throws Exception {
        return json(post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
    }

    private String message(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("message").asText();
    }

    private JsonNode find(JsonNode list, String email) {
        for (JsonNode node : list) {
            if (email.equals(node.get("email").asText())) {
                return node;
            }
        }
        return null;
    }
}
