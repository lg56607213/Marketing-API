package com.marketingagent.domain.ads;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reportapi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub"
})
@AutoConfigureMockMvc
class StatReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAnonymousCalls() throws Exception {
        mockMvc.perform(get("/api/ads/diagnostics")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/ads/stat-reports/sync")).andExpect(status().is4xxClientError());
    }

    @Test
    void servesDiagnosticsSyncAndAnalytics() throws Exception {
        mockMvc.perform(get("/api/ads/diagnostics").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.checks[0].name").value("연동 모드"));

        // 적재 전에는 조회가 400 으로 안내한다.
        mockMvc.perform(get("/api/ads/stat-reports/hourly").with(user("tester")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ads/stat-reports/sync").param("days", "1").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hourlyRows").value(480))
                .andExpect(jsonPath("$.data.failures").isEmpty());

        mockMvc.perform(get("/api/ads/stat-reports/hourly").param("device", "M").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(24))
                .andExpect(jsonPath("$.data[9].hour").value(9));

        mockMvc.perform(get("/api/ads/stat-reports/conversions").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].roas").isNumber());
    }
}
