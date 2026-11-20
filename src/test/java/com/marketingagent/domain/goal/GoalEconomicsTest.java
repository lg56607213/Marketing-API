package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketingagent.domain.goal.BusinessModel.GoalType;
import com.marketingagent.domain.goal.BusinessModel.Offering;
import com.marketingagent.domain.goal.BusinessModel.RevenueModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalEconomicsTest {

    /** 렌터카: 대여 1건 35만원, 남는 돈 10만원, 문의 10건 중 3건 계약, 고객 1명이 1.5번 빌림, 이익의 30% 를 광고비로. */
    @Test
    void derivesCpaFromWhatALeadIsWorthForADirectBusiness() {
        BusinessModel m = model(RevenueModel.DIRECT, 350_000, 100_000, 30, 1.5, GoalType.REVENUE, 21_000_000, 30);

        GoalEconomics.Result r = GoalEconomics.compute(m);

        // 문의 1건의 가치 = 100,000 × 1.5 × 30% = 45,000. 목표 CPA = 45,000 × 30% = 13,500.
        assertThat(r.valuePerLead()).isEqualTo(45_000);
        assertThat(r.breakEvenCpa()).isEqualTo(45_000);
        assertThat(r.targetCpa()).isEqualTo(13_500);
        // 매출 2,100만원 ÷ 35만원 = 계약 60건 ÷ 30% = 문의 200건.
        assertThat(r.dealsNeeded()).isEqualTo(60);
        assertThat(r.leadsNeeded()).isEqualTo(200);
        assertThat(r.budgetAtTargetCpa()).isEqualTo(200 * 13_500);
        assertThat(r.basis()).extracting(GoalEconomics.Line::label).contains("문의 1건의 가치", "목표 CPA", "필요한 문의");
    }

    /** 리스 중개: 계약 1건 수수료 80만원, 그중 60만원이 남음, 문의 20건 중 1건 계약. 계약 목표 10건. */
    @Test
    void worksTheSameForACommissionBusiness() {
        BusinessModel m = model(RevenueModel.COMMISSION, 800_000, 600_000, 5, 1, GoalType.DEALS, 10, 40);

        GoalEconomics.Result r = GoalEconomics.compute(m);

        assertThat(r.valuePerLead()).isEqualTo(30_000);   // 600,000 × 5%
        assertThat(r.targetCpa()).isEqualTo(12_000);      // × 40%
        assertThat(r.dealsNeeded()).isEqualTo(10);
        assertThat(r.leadsNeeded()).isEqualTo(200);       // 10 ÷ 5%
    }

    @Test
    void roundsUpPartialDealsAndKeepsLeadGoalsAsGiven() {
        BusinessModel profit = model(RevenueModel.DIRECT, 300_000, 70_000, 25, 1, GoalType.PROFIT, 1_000_000, 30);
        assertThat(GoalEconomics.compute(profit).dealsNeeded()).isEqualTo(15); // 14.3 → 15

        BusinessModel leads = model(RevenueModel.DIRECT, 300_000, 70_000, 25, 1, GoalType.LEADS, 80, 30);
        GoalEconomics.Result r = GoalEconomics.compute(leads);
        assertThat(r.leadsNeeded()).isEqualTo(80);
        assertThat(r.dealsNeeded()).isEqualTo(20);
    }

    @Test
    void averagesSeveralOfferingsAndRefusesIncompleteModels() {
        BusinessModel m = model(RevenueModel.DIRECT, 200_000, 50_000, 20, 1, GoalType.DEALS, 10, 50);
        Offering second = new Offering("장기 렌트");
        second.setRevenuePerDeal(600_000L);
        second.setProfitPerDeal(150_000L);
        m.getOfferings().add(second);
        GoalEconomics.Result r = GoalEconomics.compute(m);
        assertThat(r.profitPerDeal()).isEqualTo(100_000);
        assertThat(r.valuePerLead()).isEqualTo(20_000);

        m.setAdSharePct(null);
        assertThatThrownBy(() -> GoalEconomics.compute(m)).isInstanceOf(IllegalStateException.class);
    }

    static BusinessModel model(RevenueModel revenueModel, long revenue, long profit, double leadToDeal, double repeat,
            GoalType goalType, long goal, double adShare) {
        BusinessModel m = new BusinessModel();
        m.setIndustry("테스트 업종");
        m.setRevenueModel(revenueModel);
        Offering o = new Offering("기본 상품");
        o.setRevenuePerDeal(revenue);
        o.setProfitPerDeal(profit);
        m.setOfferings(new java.util.ArrayList<>(List.of(o)));
        m.setLeadToDealPct(leadToDeal);
        m.setDealsPerCustomer(repeat);
        m.setGoalType(goalType);
        m.setGoalAmount(goal);
        m.setAdSharePct(adShare);
        m.setBudgetLimit(0L);
        m.setSeedKeywords(new java.util.ArrayList<>(List.of("키워드")));
        return m;
    }
}
