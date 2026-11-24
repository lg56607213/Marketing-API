package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.AdSyncService;
import com.marketingagent.domain.ads.account.AdAccountRepository;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.domain.ads.planner.PlanService;
import com.marketingagent.domain.goal.GoalDtos.CheckinRequest;
import com.marketingagent.domain.goal.GoalDtos.CheckinResult;
import com.marketingagent.domain.goal.GoalDtos.ConfirmRequest;
import com.marketingagent.domain.goal.GoalDtos.GoalView;
import com.marketingagent.domain.goal.GoalDtos.Option;
import com.marketingagent.support.TestAccounts;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 정해진 질문(AI 없음)으로 목표 상담을 끝까지 해 보고, 확정·실제 결과 반영·업체 격리를 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:goals;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
@AutoConfigureMockMvc
class GoalConversationTest {

    @Autowired
    private GoalService goalService;
    @Autowired
    private AdSyncService adSyncService;
    @Autowired
    private PlanService planService;
    @Autowired
    private AutopilotService autopilotService;
    @Autowired
    private AdAccountRepository accountRepository;
    @Autowired
    private TestAccounts accounts;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** 렌터카 업체가 처음부터 끝까지 상담한다. */
    @Test
    void interviewsARentalBusinessThenPlansConfirmsAndRecalibrates() {
        accounts.runAs(9101L, acc -> {
            adSyncService.sync(acc, 30);

            GoalView started = goalService.start(acc);
            assertThat(started.status()).isEqualTo(GoalSession.INTERVIEW);
            assertThat(started.aiMode()).isEqualTo("rule");
            assertThat(started.messages()).hasSize(1);
            assertThat(started.messages().get(0).content()).contains("어떤 사업");

            GoalView v = answer(acc, "렌터카 대여");
            assertThat(last(v)).contains("돈은 어떻게");
            v = answer(acc, "1");
            assertThat(v.model().getRevenueModel()).isEqualTo(BusinessModel.RevenueModel.DIRECT);
            v = answer(acc, "단기 렌트, 장기 렌트");
            assertThat(v.model().getOfferings()).extracting(BusinessModel.Offering::getName).containsExactly("단기 렌트", "장기 렌트");
            assertThat(last(v)).contains("[단기 렌트]").contains("평균 매출");

            // 읽을 수 없는 답에는 예시를 들어 다시 묻는다.
            v = answer(acc, "음 그때그때 달라요");
            assertThat(last(v)).contains("금액으로").contains("[단기 렌트]");
            assertThat(v.model().getOfferings().get(0).getRevenuePerDeal()).isNull();

            answer(acc, "35만원");
            answer(acc, "10만원");
            answer(acc, "120만원");
            v = answer(acc, "30%");
            assertThat(v.model().getOfferings().get(1).getProfitPerDeal()).isEqualTo(360_000);
            assertThat(last(v)).contains("10건 들어오면");
            answer(acc, "10건 중 3건");
            answer(acc, "1.5");
            v = answer(acc, "매출 3천만원");
            assertThat(v.model().getGoalType()).isEqualTo(BusinessModel.GoalType.REVENUE);
            assertThat(v.model().getGoalAmount()).isEqualTo(30_000_000);
            v = answer(acc, "추천대로");
            assertThat(v.model().getAdShareSource()).isEqualTo(BusinessModel.SOURCE_ASSUMED);
            v = answer(acc, "200만원");
            // 동기화된 계정이라 광고비를 많이 쓴 키워드를 먼저 권한다.
            assertThat(last(v)).contains("이걸로 볼까요");
            v = answer(acc, "네");

            // 다 채우면 바로 계산과 운영안까지 나온다.
            assertThat(v.status()).isEqualTo(GoalSession.ANALYZED);
            assertThat(v.nextQuestion()).isNull();
            assertThat(v.model().getSeedKeywords()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
            GoalEconomics.Result e = v.economics();
            assertThat(e.profitPerDeal()).isEqualTo(230_000);
            // 첫 계약만 센다: 230,000 × 30% = 69,000. 재계약 1.5회는 장기 상한(103,500 × 30%)에만 쓴다.
            assertThat(e.valuePerLead()).isEqualTo(69_000);
            assertThat(e.ltvValuePerLead()).isEqualTo(103_500);
            assertThat(e.ltvCpa()).isEqualTo(31_050);
            assertThat(e.targetCpa()).isEqualTo(20_700);
            assertThat(e.dealsNeeded()).isEqualTo(39);
            assertThat(e.leadsNeeded()).isEqualTo(130);
            assertThat(last(v)).contains("계산을 마쳤어요").contains("20,700원");

            GoalDtos.Analysis analysis = v.analysis();
            assertThat(analysis.options()).isNotEmpty();
            assertThat(analysis.options()).extracting(Option::key).contains(GoalService.OPTION_GOAL);
            assertThat(analysis.recommended()).isNotNull();
            // 키워드마다 1·3·5위 입찰가와 문의 1건 비용을 붙이고, 시장 시세를 목표 CPA 와 비교한다.
            assertThat(analysis.market()).isNotNull();
            assertThat(analysis.market().keywords()).isNotEmpty();
            assertThat(analysis.market().keywords()).allSatisfy(k -> {
                assertThat(k.bid1()).isGreaterThanOrEqualTo(k.bid3());
                assertThat(k.bid3()).isGreaterThanOrEqualTo(k.bid5());
            });
            assertThat(analysis.market().marketCpa()).isPositive();
            assertThat(analysis.solution()).contains("■ 한 줄 진단").contains("3위 입찰가");
            assertThat(last(v)).contains("네이버 시세로 본 문의 1건 비용");
            Option chosen = analysis.options().stream().filter(o -> o.key().equals(analysis.recommended()))
                    .findFirst().orElseThrow();
            assertThat(chosen.plan()).isNotNull();
            assertThat(chosen.monthlyBudget()).isPositive();
            for (Option o : analysis.options()) {
                if (o.plan() != null) {
                    // 계획 엔진은 목표 CPA 를 넘는 클릭을 사지 않는다. 그래서 문의 1건 비용 예상도 목표 CPA 를 넘지 않는다.
                    assertThat(o.expectedCpa()).isLessThanOrEqualTo(e.targetCpa());
                    assertThat(o.expectedDeals()).isEqualTo(Math.round(o.expectedLeads() * 0.3));
                }
            }

            // 다 채운 뒤 말을 걸면 패널에서 고치도록 안내한다(AI 없음).
            assertThat(last(answer(acc, "고마워요"))).contains("오른쪽");

            // 확정하면 운영 계획·자동 운영 목표·계정 목표 CPA 가 모두 맞춰진다.
            GoalView confirmed = goalService.confirm(acc, new ConfirmRequest(chosen.key(), false));
            assertThat(confirmed.status()).isEqualTo(GoalSession.CONFIRMED);
            assertThat(confirmed.confirmedOption()).isEqualTo(chosen.key());
            assertThat(planService.current(acc)).isPresent();
            var settings = autopilotService.settings(acc);
            assertThat(settings.enabled()).isFalse();
            assertThat(settings.targetCpa()).isEqualTo(20_700);
            assertThat(settings.monthlyBudgetCap()).isEqualTo(chosen.monthlyBudget());
            assertThat(accountRepository.findById(acc).orElseThrow().getTargetCpa()).isEqualTo(20_700);
            assertThat(last(confirmed)).contains("확정했습니다").contains("자동 운영은 꺼져");

            // 실제 결과: 문의 20건 중 10건 계약(50%). 처음 값 30% 와 섞어 40% 로 잡는다.
            LocalDate until = LocalDate.now().minusDays(1);
            CheckinResult result = goalService.checkin(acc, new CheckinRequest(until.minusDays(6), until, 20L, 10L, null));
            assertThat(result.measuredPct()).isEqualTo(50.0);
            assertThat(result.previousLeadToDealPct()).isEqualTo(30.0);
            assertThat(result.leadToDealPct()).isEqualTo(40.0);
            assertThat(result.suggestedTargetCpa()).isEqualTo(27_600);
            assertThat(result.appliedTargetCpa()).isEqualTo(20_700);
            assertThat(result.message()).contains("바꾸시길 권합니다");
            assertThat(result.view().model().getLeadToDealSource()).isEqualTo(BusinessModel.SOURCE_MEASURED);
            assertThat(result.view().status()).isEqualTo(GoalSession.CONFIRMED);
            assertThat(result.view().checkins()).hasSize(1);
            // 제안만 하고 바로 바꾸지는 않는다.
            assertThat(autopilotService.settings(acc).targetCpa()).isEqualTo(20_700);

            goalService.applyTarget(acc);
            assertThat(autopilotService.settings(acc).targetCpa()).isEqualTo(27_600);
            assertThat(accountRepository.findById(acc).orElseThrow().getTargetCpa()).isEqualTo(27_600);

            // 계약이 문의보다 많을 수는 없다.
            assertThatThrownBy(() -> goalService.checkin(acc, new CheckinRequest(until, until, 3L, 5L, null)))
                    .isInstanceOf(BadRequestException.class);
            // 문의를 비우면 네이버 전환 수를 쓴다.
            CheckinResult fromNaver = goalService.checkin(acc, new CheckinRequest(until.minusDays(6), until, null, 1L, null));
            assertThat(fromNaver.checkin().leadsSource()).isEqualTo(GoalCheckin.LEADS_NAVER);
            assertThat(fromNaver.checkin().leads()).isPositive();

            // 다시 상담하면 사업 구조는 이어받고 목표부터 묻는다.
            GoalView again = goalService.start(acc);
            assertThat(again.sessionId()).isNotEqualTo(v.sessionId());
            assertThat(again.model().getIndustry()).isEqualTo("렌터카 대여");
            assertThat(again.model().getOfferings()).hasSize(2);
            assertThat(again.model().getGoalAmount()).isNull();
            assertThat(last(again)).contains("이번 목표만").contains("무엇을 얼마나");
        });
    }

    /** 중개 수수료 사업. 모른다는 답은 가정으로 채우고, 가정이 있으면 시험 운영을 권한다. */
    @Test
    void fillsUnknownsWithStatedAssumptionsAndRecommendsATrial() {
        accounts.runAs(9102L, acc -> {
            adSyncService.sync(acc, 30);
            goalService.start(acc);
            answer(acc, "렌트·리스 중개 에이전트");
            GoalView v = answer(acc, "수수료 받아요");
            assertThat(v.model().getRevenueModel()).isEqualTo(BusinessModel.RevenueModel.COMMISSION);
            v = answer(acc, "장기렌트 중개");
            assertThat(last(v)).contains("수수료는 얼마");
            answer(acc, "80만원");
            v = answer(acc, "전부");
            assertThat(v.model().getOfferings().get(0).getProfitPerDeal()).isEqualTo(800_000);
            v = answer(acc, "모름");
            assertThat(v.model().getLeadToDealSource()).isEqualTo(BusinessModel.SOURCE_ASSUMED);
            assertThat(v.model().getLeadToDealPct()).isEqualTo(RuleBasedInterviewer.ASSUMED_LEAD_TO_DEAL_PCT);
            assertThat(last(v)).contains("가정하고");
            answer(acc, "1");
            answer(acc, "계약 10건");
            answer(acc, "40%");
            answer(acc, "없음");
            v = answer(acc, "장기렌트, 리스 견적");

            assertThat(v.model().getSeedKeywords()).containsExactly("장기렌트", "리스 견적");
            assertThat(v.model().getBudgetLimit()).isZero();
            // 800,000 × 20% = 160,000, × 40% = 64,000. 계약 10건 ÷ 20% = 문의 50건.
            assertThat(v.economics().targetCpa()).isEqualTo(64_000);
            assertThat(v.economics().leadsNeeded()).isEqualTo(50);
            assertThat(v.analysis().notes()).anySatisfy(n -> assertThat(n).contains("가정입니다"));
            if (v.analysis().options().stream().anyMatch(o -> o.key().equals(GoalService.OPTION_TRIAL) && o.plan() != null)) {
                assertThat(v.analysis().recommended()).isEqualTo(GoalService.OPTION_TRIAL);
            }

            // 패널에서 계약 비율을 고치면 그 값은 입력값이 되고 다시 계산된다.
            BusinessModel edited = v.model();
            edited.setLeadToDealPct(25.0);
            GoalView updated = goalService.updateModel(acc, edited);
            assertThat(updated.model().getLeadToDealSource()).isEqualTo(BusinessModel.SOURCE_USER);
            assertThat(updated.economics().targetCpa()).isEqualTo(80_000);
            assertThat(updated.analysis()).isNotNull();
            assertThat(last(updated)).contains("다시 계산했습니다");

            // 범위를 벗어난 값은 버린다.
            edited = updated.model();
            edited.setAdSharePct(250.0);
            assertThat(goalService.updateModel(acc, edited).model().getAdSharePct()).isEqualTo(40.0);
        });
    }

    @Test
    void otherCompaniesCannotSeeOrUseTheConversation() throws Exception {
        String ownerToken = register("goal-owner");
        String strangerToken = register("goal-stranger");

        String created = mockMvc.perform(post("/api/ads/accounts").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"렌터카 계정\",\"provider\":\"stub\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String accountId = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(post("/api/goals/start").param("accountId", accountId).header("Authorization", ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/goals/messages").param("accountId", accountId).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"꽃배달\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/goals").param("accountId", accountId).header("Authorization", strangerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/goals/messages").param("accountId", accountId).header("Authorization", strangerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"남의 계정\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/goals").param("accountId", accountId))
                .andExpect(status().is4xxClientError());
    }

    private String register(String id) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + id + "@goal.test\",\"password\":\"pw-12345678\",\"companyName\":\"" + id + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).path("data").path("token").asText();
    }

    private GoalView answer(Long acc, String text) {
        return goalService.reply(acc, text);
    }

    private static String last(GoalView view) {
        List<GoalDtos.Message> messages = view.messages();
        return messages.get(messages.size() - 1).content();
    }
}
