package com.marketingagent.domain.goal;

import com.marketingagent.domain.goal.BusinessModel.GoalType;
import com.marketingagent.domain.goal.BusinessModel.Offering;
import com.marketingagent.domain.goal.BusinessModel.RevenueModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 사업 구조에서 "문의 1건에 광고비를 얼마까지 써도 되는가"와 "목표를 채우려면 문의가 몇 건 필요한가"를 계산한다.
 * 업종과 상관없이 같은 식을 쓴다. 저장하지 않고, 네이버도 부르지 않는다.
 *
 * <pre>
 * 문의 1건의 가치 = 계약 1건 평균 이익 × 고객 1명당 계약 횟수 × 문의→계약 비율
 * 목표 CPA       = 문의 1건의 가치 × 광고비로 쓸 비율
 * 손익분기 CPA   = 문의 1건의 가치 (이보다 비싸게 사면 광고할수록 손해)
 * 필요한 계약    = 목표 매출 ÷ 계약 1건 매출  (이익 목표면 ÷ 이익, 건수 목표면 그대로)
 * 필요한 문의    = 필요한 계약 ÷ 문의→계약 비율
 * </pre>
 */
public final class GoalEconomics {

    private GoalEconomics() {
    }

    /**
     * @param basis 숫자마다 어떻게 나왔는지. 화면에 그대로 보여 준다
     */
    public record Result(
            long revenuePerDeal,
            long profitPerDeal,
            double leadToDealPct,
            double dealsPerCustomer,
            long valuePerLead,
            long breakEvenCpa,
            long targetCpa,
            long dealsNeeded,
            long leadsNeeded,
            long budgetAtTargetCpa,
            List<Line> basis
    ) {}

    /** @param grade 입력 / 가정 / 실측 / 계산 */
    public record Line(String label, String value, String how, String grade) {}

    public static Result compute(BusinessModel m) {
        if (m.nextMissing() != null) {
            throw new IllegalStateException("아직 채우지 않은 항목이 있습니다: " + m.nextMissing());
        }
        List<Offering> offerings = m.getOfferings();
        // 상품별 비중은 묻지 않고 같다고 본다. 비중이 크게 다르면 상품마다 광고 계정·캠페인을 나눠 상담하는 편이 정확하다.
        double revenue = offerings.stream().mapToLong(Offering::getRevenuePerDeal).average().orElse(0);
        double profit = offerings.stream().mapToLong(Offering::getProfitPerDeal).average().orElse(0);
        double rate = m.getLeadToDealPct() / 100.0;
        double repeat = Math.max(1.0, m.getDealsPerCustomer());
        double share = m.getAdSharePct() / 100.0;

        long valuePerLead = Math.round(profit * repeat * rate);
        long targetCpa = Math.round(valuePerLead * share);

        double deals = switch (m.getGoalType()) {
            case REVENUE -> revenue <= 0 ? 0 : m.getGoalAmount() / revenue;
            case PROFIT -> profit <= 0 ? 0 : m.getGoalAmount() / profit;
            case DEALS -> m.getGoalAmount();
            case LEADS -> m.getGoalAmount() * rate;
        };
        long dealsNeeded = (long) Math.ceil(deals - 1e-9);
        long leadsNeeded = m.getGoalType() == GoalType.LEADS
                ? m.getGoalAmount()
                : (long) Math.ceil(dealsNeeded / rate - 1e-9);

        boolean subscription = m.getRevenueModel() == RevenueModel.SUBSCRIPTION;
        List<Line> basis = new ArrayList<>();
        basis.add(new Line(subscription ? "고객 1명 월 결제액" : "계약 1건 매출", won(revenue),
                offerings.size() > 1 ? offerings.size() + "개 상품의 평균" : offerings.get(0).getName(), "입력"));
        basis.add(new Line(subscription ? "고객 1명 월 이익" : "계약 1건 이익", won(profit),
                offerings.size() > 1 ? offerings.size() + "개 상품의 평균" : offerings.get(0).getName(),
                offerings.stream().anyMatch(o -> BusinessModel.SOURCE_ASSUMED.equals(o.getProfitSource())) ? "가정" : "입력"));
        basis.add(new Line("문의 → 계약", pct(m.getLeadToDealPct()), "문의 100건 중 계약되는 건수", grade(m.getLeadToDealSource())));
        basis.add(new Line(subscription ? "평균 유지 기간" : "고객 1명당 계약", subscription
                ? trim(repeat) + "개월" : trim(repeat) + "회", subscription ? "고객이 결제를 이어가는 평균 개월 수" : "재계약·재구매 포함",
                grade(m.getDealsPerCustomerSource())));
        basis.add(new Line("문의 1건의 가치", won(valuePerLead),
                String.format("이익 %s × %s × 계약 비율 %s", won(profit), subscription ? trim(repeat) + "개월" : trim(repeat) + "회",
                        pct(m.getLeadToDealPct())), "계산"));
        basis.add(new Line("손익분기 CPA", won(valuePerLead), "문의 1건을 이보다 비싸게 사면 광고할수록 손해", "계산"));
        basis.add(new Line("목표 CPA", won(targetCpa),
                String.format("문의 1건의 가치 × 광고비 비율 %s", pct(m.getAdSharePct())), "계산"));
        basis.add(new Line("필요한 계약", String.format("월 %,d건", dealsNeeded), goalHow(m, revenue, profit), "계산"));
        basis.add(new Line("필요한 문의", String.format("월 %,d건", leadsNeeded),
                m.getGoalType() == GoalType.LEADS ? "목표로 정한 문의 수"
                        : String.format("계약 %,d건 ÷ 계약 비율 %s", dealsNeeded, pct(m.getLeadToDealPct())), "계산"));

        return new Result(Math.round(revenue), Math.round(profit), m.getLeadToDealPct(), repeat, valuePerLead,
                valuePerLead, targetCpa, dealsNeeded, leadsNeeded, leadsNeeded * targetCpa, basis);
    }

    private static String goalHow(BusinessModel m, double revenue, double profit) {
        return switch (m.getGoalType()) {
            case REVENUE -> String.format("목표 매출 %s ÷ 계약 1건 매출 %s", won(m.getGoalAmount()), won(revenue));
            case PROFIT -> String.format("목표 이익 %s ÷ 계약 1건 이익 %s", won(m.getGoalAmount()), won(profit));
            case DEALS -> "목표로 정한 계약 수";
            case LEADS -> String.format("문의 %,d건 × 계약 비율 %s", m.getGoalAmount(), pct(m.getLeadToDealPct()));
        };
    }

    static String grade(String source) {
        if (BusinessModel.SOURCE_ASSUMED.equals(source)) {
            return "가정";
        }
        if (BusinessModel.SOURCE_MEASURED.equals(source)) {
            return "실측";
        }
        return "입력";
    }

    static String won(double amount) {
        return String.format("%,d원", Math.round(amount));
    }

    static String pct(double value) {
        return trim(value) + "%";
    }

    static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 10) / 10.0);
    }
}
