package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import com.marketingagent.domain.goal.GoalDtos.GoalView;
import com.marketingagent.support.TestAccounts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.marketingagent.domain.onboarding.SiteInspector;
import java.util.Map;

/**
 * AI 상담: 넓게 한 번 묻고, 긴 답에서 한꺼번에 추린 뒤, 빠진 꼭 필요한 항목만 묶어서 되묻고,
 * 나머지는 가정으로 채워 계산·키워드 입찰가 조사·종합 제안까지 간다. 모델 응답은 정해 둔 가짜다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:goalsai;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=fake"
})
class GoalAiConversationTest {

    /** 상담 응답을 차례로 돌려주는 가짜 모델. 종합 제안 요청에는 서버가 넘긴 자료를 기록하고 정해진 글을 준다. */
    static class ScriptedAi implements AiProvider {
        final List<String> interviews = new ArrayList<>();
        final List<String> solutionInputs = new ArrayList<>();

        @Override
        public AiResponse generate(AiRequest request) {
            if (request.systemPrompt().contains("선도 업체를 최대")) {
                return new AiResponse("{\"competitors\": [{\"name\": \"선도렌트\", \"url\": \"https://leader.example.com\"}]}", 1, 1);
            }
            if (request.systemPrompt().contains("종합 제안")) {
                solutionInputs.add(request.userPrompt());
                return new AiResponse("■ 한 줄 진단\n• 첫 계약만으로는 시세가 비쌉니다.", 1, 1);
            }
            return new AiResponse(interviews.isEmpty() ? "{}" : interviews.remove(0), 1, 1);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ScriptedAi scriptedAi() {
            return new ScriptedAi();
        }

        /** 경쟁사 홈페이지를 실제로 열지 않고 정해 둔 소스를 준다. */
        @Bean
        @Primary
        SiteInspector fakeSiteInspector() {
            return new ChannelScannerTest.FakeInspector(Map.of("https://leader.example.com", ChannelScannerTest.RIVAL_HTML));
        }
    }

    @Autowired
    private GoalService goalService;
    @Autowired
    private ScriptedAi ai;
    @Autowired
    private TestAccounts accounts;

    @BeforeEach
    void reset() {
        ai.interviews.clear();
        ai.solutionInputs.clear();
    }

    @Test
    void asksBroadlyThenOnlyForMissingEssentialsThenProposesASolution() {
        accounts.runAs(9201L, acc -> {
            GoalView started = goalService.start(acc);
            assertThat(started.aiMode()).isEqualTo("ai");
            assertThat(started.messages().get(0).content()).contains("편하게 소개").contains("고객층");

            // 첫 답: 사업·고객층·상품·키워드 묶음은 나왔지만 목표가 빠졌다 → 목표만 되묻는다.
            ai.interviews.add("""
                    {"reply": "정리했어요.\\n1) 한 달 목표는 무엇인가요?",
                     "model": {"industry": "차량 렌트·리스 중개", "revenueModel": "COMMISSION",
                               "offerings": [{"name": "장기렌트", "revenuePerDeal": 1500000, "profitPerDeal": 1500000, "sharePct": 30},
                                             {"name": "장기리스", "revenuePerDeal": 800000, "profitPerDeal": 800000, "sharePct": 70}],
                               "leadToDealPct": 10, "dealsPerCustomer": 4,
                               "segments": ["법인", "화물차 사업자", "저신용 개인"],
                               "concerns": "문의 단가가 비싸다",
                               "keywordGroups": [{"name": "법인 · 견적", "keywords": ["법인 장기렌트 견적", "법인차 리스"]},
                                                 {"name": "화물차 · 조건", "keywords": ["1톤 화물차 리스", "화물차 장기렌트"]}]},
                     "unknown": []}
                    """);
            GoalView v = goalService.reply(acc, "렌트 리스 중개합니다. 법인, 화물차, 저신용 고객이 있고 ...");
            assertThat(v.status()).isEqualTo(GoalSession.INTERVIEW);
            assertThat(last(v)).contains("한 달 목표");
            assertThat(v.model().getSegments()).containsExactly("법인", "화물차 사업자", "저신용 개인");
            assertThat(v.model().getKeywordGroups()).hasSize(2);
            assertThat(v.model().getOfferings().get(1).getSharePct()).isEqualTo(70.0);

            // 둘째 답: 목표가 채워지면 광고비 비율·상한·핵심 키워드는 묻지 않고 가정으로 채워 바로 계산한다.
            ai.interviews.add("""
                    {"reply": "이 내용으로 계산과 키워드 조사를 할게요.",
                     "model": {"goalType": "REVENUE", "goalAmount": 20000000}, "unknown": []}
                    """);
            v = goalService.reply(acc, "매출 2천만원이요");
            assertThat(v.status()).isEqualTo(GoalSession.ANALYZED);
            assertThat(v.model().getAdShareSource()).isEqualTo(BusinessModel.SOURCE_ASSUMED);
            assertThat(v.model().getBudgetLimit()).isZero();
            assertThat(v.model().getSeedKeywords()).contains("법인 장기렌트 견적");

            // 첫 계약 기준: 가중평균 이익 1,010,000 × 10% × 30% = 30,300. 재계약 4회는 장기 상한에만.
            assertThat(v.economics().targetCpa()).isEqualTo(30_300);
            assertThat(v.economics().ltvCpa()).isEqualTo(121_200);

            GoalDtos.Analysis analysis = v.analysis();
            assertThat(analysis.market().keywords()).extracting(KeywordBidResearch.KeywordBid::group)
                    .contains("법인 · 견적", "화물차 · 조건");
            assertThat(analysis.solution()).startsWith("■ 한 줄 진단");
            assertThat(last(v)).contains("한 달 광고비 상한은 말씀이 없어서").contains("■ 한 줄 진단");

            // 경쟁사를 말하지 않았으면 AI 가 업계 선도 업체를 추정하고, 그 홈페이지의 흔적으로 채널을 권한다.
            assertThat(v.model().getCompetitors()).extracting(BusinessModel.Competitor::getSource).containsOnly("AI");
            assertThat(analysis.competition()).isNotNull();
            assertThat(analysis.competition().suggestions()).extracting(ChannelScanner.ChannelSuggestion::channel)
                    .contains("메타(페이스북·인스타그램) 광고");
            assertThat(last(v)).contains("경쟁사·선도 업체 1곳의 광고 채널을 살펴봤습니다");

            // 종합 제안에는 서버가 계산·조사한 숫자를 넘긴다.
            assertThat(ai.solutionInputs).hasSize(1);
            assertThat(ai.solutionInputs.get(0)).contains("목표CPA_첫계약기준").contains("입찰가_3위").contains("법인")
                    .contains("경쟁사분석").contains("메타 픽셀(fbq)");
        });
    }

    @Test
    void stopsAskingAfterTwoRoundsAndFallsBackToTheFixedQuestion() {
        accounts.runAs(9202L, acc -> {
            goalService.start(acc);
            ai.interviews.add("""
                    {"reply": "어떤 상품을 파시나요?", "model": {"industry": "꽃집"}, "unknown": []}
                    """);
            GoalView v = goalService.reply(acc, "꽃집이요");
            assertThat(last(v)).isEqualTo("어떤 상품을 파시나요?");

            ai.interviews.add("""
                    {"reply": "다시 여쭤볼게요. 돈은 어떻게 버시나요?", "model": {}, "unknown": []}
                    """);
            v = goalService.reply(acc, "글쎄요");
            // 두 번 되물었으면 AI 문장 대신 그 항목만 콕 집는 정해진 질문으로 묻는다.
            assertThat(last(v)).contains("돈은 어떻게 버시나요?").contains("1) 직접 팔거나");
        });
    }

    private static String last(GoalView v) {
        return v.messages().get(v.messages().size() - 1).content();
    }
}
