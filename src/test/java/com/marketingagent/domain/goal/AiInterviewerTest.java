package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.ai.AiResponse;
import com.marketingagent.domain.goal.BusinessModel.Offering;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 모델이 준 값은 범위 검사를 거쳐서만 받아들이고, 읽을 수 없는 응답이면 정해진 질문으로 넘긴다. */
class AiInterviewerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsValidFieldsEvenInsideACodeBlock() {
        String raw = """
                네, 정리해 볼게요.
                ```json
                {"reply": "수수료는 얼마인가요?",
                 "model": {"industry": "리스 중개", "revenueModel": "commission",
                           "offerings": [{"name": "장기렌트"}], "goalType": "DEALS", "goalAmount": 12},
                 "unknown": ["leadToDealPct"]}
                ```
                """;
        AiInterviewer ai = new AiInterviewer(request -> new AiResponse(raw, 10, 10), objectMapper);

        Optional<AiInterviewer.Turn> turn = ai.next(new BusinessModel(), List.of(), "리스 중개해요", null, "업종?");

        assertThat(turn).isPresent();
        BusinessModel m = turn.get().model();
        assertThat(m.getIndustry()).isEqualTo("리스 중개");
        assertThat(m.getRevenueModel()).isEqualTo(BusinessModel.RevenueModel.COMMISSION);
        assertThat(m.getOfferings()).extracting(Offering::getName).containsExactly("장기렌트");
        assertThat(m.getGoalType()).isEqualTo(BusinessModel.GoalType.DEALS);
        assertThat(m.getGoalAmount()).isEqualTo(12);
        assertThat(turn.get().reply()).isEqualTo("수수료는 얼마인가요?");
        assertThat(turn.get().unknown()).containsExactly("leadToDealPct");
    }

    @Test
    void dropsOutOfRangeOrInconsistentValues() throws Exception {
        BusinessModel current = new BusinessModel();
        Offering o = new Offering("단기 렌트");
        o.setRevenuePerDeal(300_000L);
        current.setOfferings(new ArrayList<>(List.of(o)));
        current.setLeadToDealPct(20.0);

        AiInterviewer ai = new AiInterviewer(request -> new AiResponse("{}", 0, 0), objectMapper);
        BusinessModel merged = ai.merge(current, objectMapper.readTree("""
                {"leadToDealPct": 150, "revenueModel": "BARTER", "adSharePct": -5, "dealsPerCustomer": 0.5,
                 "goalType": "REVENUE", "offerings": [{"name": "단기 렌트", "profitPerDeal": 900000}],
                 "budgetLimit": -1, "seedKeywords": ["", "렌트카"]}
                """));

        assertThat(merged.getLeadToDealPct()).isEqualTo(20.0);
        assertThat(merged.getRevenueModel()).isNull();
        assertThat(merged.getAdSharePct()).isNull();
        assertThat(merged.getDealsPerCustomer()).isNull();
        // 금액 없이 목표 종류만 오면 받지 않는다.
        assertThat(merged.getGoalType()).isNull();
        // 남는 돈이 매출보다 클 수 없다.
        assertThat(merged.getOfferings().get(0).getProfitPerDeal()).isNull();
        assertThat(merged.getOfferings().get(0).getRevenuePerDeal()).isEqualTo(300_000);
        assertThat(merged.getBudgetLimit()).isNull();
        assertThat(merged.getSeedKeywords()).containsExactly("렌트카");
    }

    @Test
    void givesUpQuietlyWhenTheModelAnswersWithoutJson() {
        AiInterviewer ai = new AiInterviewer(request -> new AiResponse("죄송해요, 다시 말씀해 주세요.", 0, 0), objectMapper);
        assertThat(ai.next(new BusinessModel(), List.of(), "안녕", null, null)).isEmpty();

        AiInterviewer failing = new AiInterviewer(request -> {
            throw new IllegalStateException("timeout");
        }, objectMapper);
        assertThat(failing.next(new BusinessModel(), List.of(), "안녕", null, null)).isEmpty();
    }
}
