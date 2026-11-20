package com.marketingagent.domain.ads;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reportapi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub"
})
@AutoConfigureMockMvc
class StatReportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsAnonymousCalls() throws Exception {
        mockMvc.perform(get("/api/ads/diagnostics")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/ads/stat-reports/sync")).andExpect(status().is4xxClientError());
    }

    @Test
    void servesDiagnosticsSyncAndAnalytics() throws Exception {
        String registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reports@api.test\",\"password\":\"pw-12345678\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readTree(registered).path("data").path("token").asText();
        String created = mockMvc.perform(post("/api/ads/accounts").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"예시 계정\",\"provider\":\"stub\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String accountId = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(get("/api/ads/diagnostics").param("accountId", accountId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.checks[0].name").value("연동 모드"));

        // 적재 전에는 조회가 400 으로 안내한다.
        mockMvc.perform(get("/api/ads/stat-reports/hourly").param("accountId", accountId).header("Authorization", token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ads/stat-reports/sync").param("accountId", accountId).param("days", "1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hourlyRows").value(480))
                .andExpect(jsonPath("$.data.failures").isEmpty());

        mockMvc.perform(get("/api/ads/stat-reports/hourly").param("accountId", accountId).param("device", "M").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(24))
                .andExpect(jsonPath("$.data[9].hour").value(9));

        mockMvc.perform(get("/api/ads/stat-reports/conversions").param("accountId", accountId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].roas").isNumber());
    }
}
