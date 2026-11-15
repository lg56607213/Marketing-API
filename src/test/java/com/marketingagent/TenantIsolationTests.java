package com.marketingagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 업체 격리와 캠페인 범위가 실제 HTTP 경로에서 지켜지는지 확인한다.
 * 예시 데이터 계정(stub)을 쓰므로 네이버를 호출하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenant-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "ads.bid.dry-run=false"
})
@AutoConfigureMockMvc
class TenantIsolationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anotherCompanyCannotSeeOrTouchAdData() throws Exception {
        String ownerA = register("a-owner@tenant.test", "Company A");
        String ownerB = register("b-owner@tenant.test", "Company B");

        long accountA = createStubAccount(ownerA, "A demo");
        send(post("/api/ads/sync").param("accountId", String.valueOf(accountA)).param("days", "7"), ownerA)
                .andExpect(status().isOk());

        // A 는 자기 계정을 본다
        JsonNode mine = data(send(get("/api/ads/accounts"), ownerA).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);

        // B 에게는 A 의 계정이 목록에도 없고, ID 를 직접 넣어도 조회·변경되지 않는다
        JsonNode theirs = data(send(get("/api/ads/accounts"), ownerB).andExpect(status().isOk()));
        assertThat(theirs).isEmpty();
        String id = String.valueOf(accountA);
        send(get("/api/ads/summary").param("accountId", id), ownerB).andExpect(status().isNotFound());
        send(get("/api/ads/campaigns").param("accountId", id), ownerB).andExpect(status().isNotFound());
        send(post("/api/ads/sync").param("accountId", id), ownerB).andExpect(status().isNotFound());
        send(post("/api/ads/bids/recommend").param("accountId", id), ownerB).andExpect(status().isNotFound());

        // A 가 만든 추천을 B 가 승인할 수 없다
        JsonNode recommendations = data(send(post("/api/ads/bids/recommend").param("accountId", id), ownerA)
                .andExpect(status().isOk()));
        assertThat(recommendations).isNotEmpty();
        long recommendationId = recommendations.get(0).get("id").asLong();
        send(post("/api/ads/bids/" + recommendationId + "/approve"), ownerB).andExpect(status().isNotFound());
        send(post("/api/ads/bids/" + recommendationId + "/approve"), ownerA).andExpect(status().isOk());
    }

    @Test
    void campaignFilterSplitsTotalsAndTargetCpaIsPerCampaign() throws Exception {
        String owner = register("c-owner@tenant.test", "Company C");
        String id = String.valueOf(createStubAccount(owner, "C demo"));
        send(post("/api/ads/sync").param("accountId", id).param("days", "14"), owner).andExpect(status().isOk());

        JsonNode campaigns = data(send(get("/api/ads/campaigns").param("accountId", id), owner)
                .andExpect(status().isOk()));
        assertThat(campaigns).hasSize(2);
        String first = campaigns.get(0).get("nccCampaignId").asText();
        String second = campaigns.get(1).get("nccCampaignId").asText();

        long all = spend(owner, id, null);
        long spendFirst = spend(owner, id, first);
        long spendSecond = spend(owner, id, second);
        assertThat(spendFirst).isPositive();
        assertThat(spendSecond).isPositive();
        assertThat(spendFirst + spendSecond).isEqualTo(all);

        send(get("/api/ads/summary").param("accountId", id).param("campaignId", "cmp-unknown"), owner)
                .andExpect(status().isNotFound());

        // 같은 계정이라도 캠페인 목표가 다르면 추천 방향이 갈린다: 빡빡한 목표는 하향, 넉넉한 목표는 상향
        send(put("/api/ads/campaigns/" + first + "/target-cpa").param("accountId", id).param("targetCpa", "100"),
                owner).andExpect(status().isOk());
        send(put("/api/ads/campaigns/" + second + "/target-cpa").param("accountId", id)
                .param("targetCpa", "10000000"), owner).andExpect(status().isOk());

        JsonNode recommendations = data(send(post("/api/ads/bids/recommend").param("accountId", id), owner)
                .andExpect(status().isOk()));
        assertThat(recommendations).isNotEmpty();
        for (JsonNode recommendation : recommendations) {
            String campaign = recommendation.get("nccCampaignId").asText();
            String direction = recommendation.get("direction").asText();
            assertThat(direction).isEqualTo(campaign.equals(first) ? "DOWN" : "UP");
        }

        // 캠페인을 지정해 조회하면 그 캠페인 추천만 나온다
        JsonNode pendingFirst = data(send(get("/api/ads/bids").param("accountId", id).param("campaignId", first),
                owner).andExpect(status().isOk()));
        assertThat(pendingFirst).isNotEmpty();
        pendingFirst.forEach(r -> assertThat(r.get("nccCampaignId").asText()).isEqualTo(first));
    }

    @Test
    void brandsAreScopedToCompanyAndRequireLogin() throws Exception {
        String ownerA = register("d-owner@tenant.test", "Company D");
        String ownerB = register("e-owner@tenant.test", "Company E");

        JsonNode brand = data(send(post("/api/brand").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Brand D\"}"), ownerA).andExpect(status().isCreated()));
        long brandId = brand.get("id").asLong();

        assertThat(data(send(get("/api/brand"), ownerA).andExpect(status().isOk()))).hasSize(1);
        assertThat(data(send(get("/api/brand"), ownerB).andExpect(status().isOk()))).isEmpty();
        send(get("/api/brand/" + brandId), ownerB).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/brand")).andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String register(String email, String companyName) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, String>() {{
            put("email", email);
            put("password", "test-pass-1234");
            put("companyName", companyName);
        }});
        JsonNode data = data(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
        assertThat(data.get("role").asText()).isEqualTo("ADMIN");
        assertThat(data.get("companyName").asText()).isEqualTo(companyName);
        return data.get("token").asText();
    }

    private long createStubAccount(String token, String name) throws Exception {
        JsonNode data = data(send(post("/api/ads/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"provider\":\"stub\"}"), token)
                .andExpect(status().isCreated()));
        return data.get("id").asLong();
    }

    private long spend(String token, String accountId, String campaignId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/ads/summary").param("accountId", accountId);
        if (campaignId != null) {
            request.param("campaignId", campaignId);
        }
        return data(send(request, token).andExpect(status().isOk())).get("salesAmt").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request,
            String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private JsonNode data(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("data");
    }
}
