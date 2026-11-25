package com.marketingagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.goal.GoalSession;
import com.marketingagent.domain.goal.GoalSessionRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 초보 업체의 완전 일임 흐름. 목표 상담만 하고 제안안은 확정하지 않은 상태에서 자동 운영을 켰을 때
 * 상담 결과를 목표로 쓰고, 실시간 입찰·키워드 추가·지켜보기·보고 메일이 동작하는지 확인한다.
 * 예시 데이터 계정(stub)을 쓰므로 네이버를 호출하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:autonomous-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "ads.bid.dry-run=false"
})
@AutoConfigureMockMvc
class AutonomousOperationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GoalSessionRepository goalSessionRepository;

    @Test
    void beginnerCanDelegateEverythingAfterConsultationOnly() throws Exception {
        String token = register("beginner@auto.test", "초보업체");
        long accountId = createStubAccount(token);
        String id = String.valueOf(accountId);
        send(post("/api/ads/sync").param("accountId", id).param("days", "30"), token).andExpect(status().isOk());

        // 상담만 하고 확정은 안 함: 목표 CPA·손익분기·월 상한·상담 키워드만 있다
        GoalSession session = new GoalSession(accountId, """
                {"industry":"화물차 리스","offerings":[{"name":"화물차 리스"}],"segments":["화물차 사업자"],
                 "budgetLimit":3000000,"seedKeywords":["특장차 리스 견적"]}""");
        session.analyzed("""
                {"economics":{"targetCpa":49500,"breakEvenCpa":165000}}""");
        goalSessionRepository.save(session);

        // 자동 운영만 켜고 목표·한도는 비워 둔다
        send(put("/api/ads/autopilot").param("accountId", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"targetCpa\":0,\"targetRoasPct\":0,\"monthlyBudgetCap\":0,"
                        + "\"manageBids\":true,\"manageBudget\":true,\"pauseWasteful\":true}"), token)
                .andExpect(status().isOk());

        JsonNode settings = data(send(get("/api/ads/autopilot").param("accountId", id), token).andExpect(status().isOk()));
        assertThat(settings.get("effectiveTargetCpa").asLong()).isEqualTo(49500);
        assertThat(settings.get("targetSource").asText()).isEqualTo("목표 상담 결과");
        assertThat(settings.get("effectiveMonthlyBudgetCap").asLong()).isEqualTo(3_000_000);
        assertThat(settings.get("effectiveMaxCpc").asLong()).as("손익분기 × 전환율로 자동 계산").isPositive();
        assertThat(settings.get("realtimeBids").asBoolean()).as("초보자용 기본값은 모두 켜짐").isTrue();
        assertThat(settings.get("addKeywords").asBoolean()).isTrue();

        // 클릭 1회 최대 500원으로 묶고 실시간 입찰을 계산하면, 어떤 키워드도 500원을 넘지 않고 한 번에 20%까지만 바뀐다
        send(put("/api/ads/autopilot/delegation").param("accountId", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"addKeywords\":true,\"addNegatives\":true,\"cleanupKeywords\":true,\"realtimeBids\":true,"
                        + "\"targetRank\":3,\"maxCpc\":500,\"observeDays\":0}"), token)
                .andExpect(status().isOk());
        JsonNode realtime = data(send(post("/api/ads/autopilot/realtime").param("accountId", id).param("preview", "true"),
                token).andExpect(status().isOk()));
        assertThat(realtime.get("actions")).isNotEmpty();
        for (JsonNode action : realtime.get("actions")) {
            long before = won(action.get("before").asText());
            long after = won(action.get("after").asText());
            assertThat(after).isLessThanOrEqualTo(500);
            if (before <= 500) {
                // 최대 금액 안에서는 한 번에 변동폭만큼만 따라간다.
                // 최대 금액을 넘던 입찰가는 변동폭과 관계없이 바로 최대 금액 이하로 내린다(위에서 확인).
                assertThat(Math.abs(after - before)).isLessThanOrEqualTo(Math.max(10, Math.round(before * 0.2)) + 10);
            }
            assertThat(action.get("status").asText()).isEqualTo("미리보기");
        }

        // 매일 실행 미리보기: 상담 키워드를 넣자고 한다(AI 를 쓰지 않는 서버에서는 규칙으로 판단)
        JsonNode preview = data(send(post("/api/ads/autopilot/run").param("accountId", id).param("preview", "true"),
                token).andExpect(status().isOk()));
        assertThat(preview.get("actions").findValuesAsText("target")).contains("특장차 리스 견적");

        // 지켜보기 7일: 실제 실행해도 기록만 남는다
        JsonNode observed = data(send(put("/api/ads/autopilot/delegation").param("accountId", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addKeywords\":true,\"addNegatives\":true,\"cleanupKeywords\":true,\"realtimeBids\":true,"
                        + "\"targetRank\":3,\"maxCpc\":500,\"observeDays\":7}"), token).andExpect(status().isOk()));
        assertThat(observed.get("recordOnly").asBoolean()).isTrue();
        JsonNode run = data(send(post("/api/ads/autopilot/run").param("accountId", id).param("preview", "false"),
                token).andExpect(status().isOk()));
        assertThat(run.get("dryRun").asBoolean()).isTrue();
        for (JsonNode action : run.get("actions")) {
            assertThat(action.get("status").asText()).isEqualTo("기록만");
        }
        assertThat(run.get("notes").toString()).contains("지켜보기");
    }

    @Test
    void reportMailRecipientsArePerAccountAndPreviewShowsBalance() throws Exception {
        String token = register("report@auto.test", "보고업체");
        String id = String.valueOf(createStubAccount(token));
        send(post("/api/ads/sync").param("accountId", id).param("days", "14"), token).andExpect(status().isOk());

        send(put("/api/ads/report-mail").param("accountId", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"recipients\":[\"not-an-email\"]}"), token)
                .andExpect(status().isBadRequest());
        JsonNode saved = data(send(put("/api/ads/report-mail").param("accountId", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"recipients\":[\"ceo@auto.test\",\" ceo@auto.test \",\"cfo@auto.test\"]}"), token)
                .andExpect(status().isOk()));
        assertThat(saved.get("recipients")).as("공백·중복 정리").hasSize(2);
        assertThat(saved.get("mailConfigured").asBoolean()).as("테스트 서버에는 메일 서버가 없다").isFalse();

        JsonNode preview = data(send(get("/api/ads/report-mail/preview").param("accountId", id), token)
                .andExpect(status().isOk()));
        assertThat(preview.get("subject").asText()).contains("광고 보고");
        String html = preview.get("html").asText();
        assertThat(html).contains("남은 광고비(비즈머니)").contains("300,000원").contains("클릭률").contains("전환율");

        // 메일 서버가 없으면 왜 못 보내는지 알려 준다
        String message = objectMapper.readTree(send(post("/api/ads/report-mail/send").param("accountId", id), token)
                .andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("message").asText();
        assertThat(message).contains("메일 서버");
    }

    // --- helpers ---

    private static long won(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }

    private String register(String email, String companyName) throws Exception {
        JsonNode data = data(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"test-pass-1234\",\"companyName\":\"" + companyName + "\"}"))
                .andExpect(status().isCreated()));
        return data.get("token").asText();
    }

    private long createStubAccount(String token) throws Exception {
        return data(send(post("/api/ads/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"예시\",\"provider\":\"stub\"}"), token)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
