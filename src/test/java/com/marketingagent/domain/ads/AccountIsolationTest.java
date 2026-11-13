package com.marketingagent.domain.ads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.security.CredentialCipher;
import com.marketingagent.common.security.OwnerContext;
import com.marketingagent.domain.ads.account.NaverAccount;
import com.marketingagent.domain.ads.account.NaverAccountRepository;
import com.marketingagent.domain.ads.account.NaverAccountService;
import com.marketingagent.domain.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 회원 한 명의 광고 데이터와 네이버 키가 다른 회원에게 보이거나 지워지지 않는지 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:isolation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub"
})
@AutoConfigureMockMvc
class AccountIsolationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NaverAccountRepository accountRepository;
    @Autowired
    private NaverAccountService accountService;
    @Autowired
    private CredentialCipher cipher;
    @Autowired
    private ReportSyncService reportSyncService;
    @Autowired
    private KeywordStatHourlyRepository hourlyRepository;
    @Autowired
    private AdCampaignRepository campaignRepository;

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "pw-12345678"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).path("data").path("token").asText();
    }

    private long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void eachMemberSeesOnlyTheirOwnAdData() throws Exception {
        String alice = register("alice@iso.test");
        String bob = register("bob@iso.test");
        String carol = register("carol@iso.test");

        mockMvc.perform(post("/api/ads/sync").param("days", "2").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywords").value(10));
        // 같은 네이버 ID 를 가진 데이터를 다른 회원이 받아도 충돌하지 않는다.
        mockMvc.perform(post("/api/ads/sync").param("days", "2").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywords").value(10));

        mockMvc.perform(get("/api/ads/campaigns").header("Authorization", alice))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/ads/campaigns").header("Authorization", bob))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/ads/keywords").header("Authorization", alice))
                .andExpect(jsonPath("$.data.length()").value(10));

        // 아무것도 받지 않은 회원에게는 남의 데이터가 보이지 않는다.
        mockMvc.perform(get("/api/ads/campaigns").header("Authorization", carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/ads/keywords").header("Authorization", carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/ads/search-queries").header("Authorization", carol))
                .andExpect(status().isBadRequest());

        // 리포트는 한 회원만 받는다. 다른 회원 조회에는 잡히지 않는다.
        mockMvc.perform(post("/api/ads/stat-reports/sync").param("days", "1").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hourlyRows").value(480));
        mockMvc.perform(get("/api/ads/stat-reports/hourly").header("Authorization", alice))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ads/stat-reports/hourly").header("Authorization", bob))
                .andExpect(status().isBadRequest());

        // 입찰 추천도 회원별이다.
        mockMvc.perform(post("/api/ads/bids/recommend").header("Authorization", alice))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ads/bids").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 소유자가 없는 상태에서는 아무것도 보이지 않는다.
        assertThat(campaignRepository.count()).isZero();
        assertThat((long) OwnerContext.<Long>callAs(userId("alice@iso.test"), campaignRepository::count)).isEqualTo(1);
    }

    @Test
    void resyncReplacesOnlyTheCallersRows() {
        long first = 9001L;
        long second = 9002L;
        OwnerContext.runAs(first, () -> reportSyncService.sync(1));
        OwnerContext.runAs(second, () -> reportSyncService.sync(1));

        long secondIdBefore = OwnerContext.callAs(second,
                () -> hourlyRepository.findAll().get(0).getId());
        // 다시 받으면 그 날짜를 지우고 새로 넣는다. 지워지는 것은 자기 것뿐이어야 한다.
        OwnerContext.runAs(first, () -> reportSyncService.sync(1));

        assertThat((long) OwnerContext.<Long>callAs(first, hourlyRepository::count)).isEqualTo(480);
        assertThat((long) OwnerContext.<Long>callAs(second, hourlyRepository::count)).isEqualTo(480);
        assertThat(OwnerContext.callAs(second, () -> hourlyRepository.findAll().get(0).getId()))
                .isEqualTo(secondIdBefore);
        assertThat(OwnerContext.callAs(second, () -> hourlyRepository.findAll().get(0).getOwnerId()))
                .isEqualTo(second);
    }

    @Test
    void naverKeysAreStoredEncryptedAndNeverReturned() throws Exception {
        String dave = register("dave@iso.test");
        String erin = register("erin@iso.test");
        String apiKey = "test-access-license-0001";
        String secretKey = "test-secret-value-zzzz";

        String response = mockMvc.perform(put("/api/ads/account").header("Authorization", dave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerId", "1234567", "apiKey", apiKey, "secretKey", secretKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.customerId").value("1234567"))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("****0001"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(apiKey).doesNotContain(secretKey);

        NaverAccount stored = accountRepository.findByUserId(userId("dave@iso.test")).orElseThrow();
        assertThat(stored.getApiKeyEnc()).doesNotContain(apiKey);
        assertThat(stored.getSecretKeyEnc()).doesNotContain(secretKey);
        assertThat(cipher.decrypt(stored.getSecretKeyEnc())).isEqualTo(secretKey);
        assertThat(OwnerContext.callAs(userId("dave@iso.test"), accountService::current).secretKey())
                .isEqualTo(secretKey);

        // 다른 회원에게는 연결이 보이지 않는다.
        mockMvc.perform(get("/api/ads/account").header("Authorization", erin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.customerId").doesNotExist());

        mockMvc.perform(put("/api/ads/account").header("Authorization", erin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerId", "abc", "apiKey", "k", "secretKey", "s"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/ads/account").header("Authorization", dave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false));
        assertThat(accountRepository.findByUserId(userId("dave@iso.test"))).isEmpty();

        mockMvc.perform(get("/api/ads/account")).andExpect(status().is4xxClientError());
    }
}
