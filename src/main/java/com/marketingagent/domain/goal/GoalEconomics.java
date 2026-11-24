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
 * <p>목표 CPA 는 첫 계약만으로 계산한다. 재계약·재구매는 몇 년 뒤에 들어오는 돈이라 이번 달 광고비의 근거로 쓰면
 * 숫자가 부풀어 현실과 동떨어진다. 재계약까지 친 값은 "장기 가치 기준 상한"으로 따로 보여 준다.
 * 정기 결제는 계약 1건이 곧 여러 달 결제라서 첫 계약을 최대 12개월치로 본다.
 *
 * <pre>
 * 문의 1건의 가치(첫 계약) = 계약 1건 평균 이익 × 문의→계약 비율   (정기 결제면 × min(유지 개월, 12))
 * 목표 CPA                 = 문의 1건의 가치 × 광고비로 쓸 비율
 * 손익분기 CPA             = 문의 1건의 가치 (이보다 비싸게 사면 첫 계약만으로는 손해)
 * 장기 가치 기준 상한      = 계약 1건 이익 × 고객 1명당 계약 횟수 × 문의→계약 비율 × 광고비 비율
 * 필요한 계약              = 목표 매출 ÷ 계약 1건 매출  (이익 목표면 ÷ 이익, 건수 목표면 그대로)
 * 필요한 문의              = 필요한 계약 ÷ 문의→계약 비율
 * </pre>
 *
 * <p>상품이 여럿이면 판매 비중을 모두 알 때 비중대로, 아니면 똑같다고 보고 평균을 낸다.
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
            List<Line> basis,
            long ltvValuePerLead,
            long ltvCpa
    ) {}

    /** 정기 결제의 첫 계약을 몇 달치까지 볼지 */
    static final double SUBSCRIPTION_FIRST_DEAL_MONTHS = 12;

    /** @param grade 입력 / 가정 / 실측 / 계산 */
    public record Line(String label, String value, String how, String grade) {}

    public static Result compute(BusinessModel m) {
        if (m.nextMissing() != null) {
            throw new IllegalStateException("아직 채우지 않은 항목이 있습니다: " + m.nextMissing());
        }
        List<Offering> offerings = m.getOfferings();
        boolean weighted = offerings.size() > 1
                && offerings.stream().allMatch(o -> o.getSharePct() != null && o.getSharePct() > 0);
        double revenue = average(offerings, weighted, Offering::getRevenuePerDeal);
        double profit = average(offerings, weighted, Offering::getProfitPerDeal);
        double rate = m.getLeadToDealPct() / 100.0;
        double repeat = Math.max(1.0, m.getDealsPerCustomer());
        double share = m.getAdSharePct() / 100.0;
        boolean subscription = m.getRevenueModel() == RevenueModel.SUBSCRIPTION;
        double firstDeal = subscription ? Math.min(repeat, SUBSCRIPTION_FIRST_DEAL_MONTHS) : 1.0;

        long valuePerLead = Math.round(profit * firstDeal * rate);
        long targetCpa = Math.round(valuePerLead * share);
        long ltvValuePerLead = Math.round(profit * repeat * rate);
        long ltvCpa = Math.round(ltvValuePerLead * share);

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

        List<Line> basis = new ArrayList<>();
        String mix = offerings.size() == 1 ? offerings.get(0).getName()
                : offerings.size() + "개 상품의 " + (weighted ? "판매 비중 가중평균" : "평균(비중을 몰라 같다고 봄)");
        basis.add(new Line(subscription ? "고객 1명 월 결제액" : "계약 1건 매출", won(revenue), mix, "입력"));
        basis.add(new Line(subscription ? "고객 1명 월 이익" : "계약 1건 이익", won(profit), mix,
                offerings.stream().anyMatch(o -> BusinessModel.SOURCE_ASSUMED.equals(o.getProfitSource())) ? "가정" : "입력"));
        basis.add(new Line("문의 → 계약", pct(m.getLeadToDealPct()), "문의 100건 중 계약되는 건수", grade(m.getLeadToDealSource())));
        basis.add(new Line(subscription ? "평균 유지 기간" : "고객 1명당 계약", subscription
                ? trim(repeat) + "개월" : trim(repeat) + "회",
                subscription ? "고객이 결제를 이어가는 평균 개월 수" : "재계약·재구매 포함. 목표 CPA 에는 넣지 않고 장기 상한에만 씀",
                grade(m.getDealsPerCustomerSource())));
        basis.add(new Line("문의 1건의 가치(첫 계약)", won(valuePerLead), subscription
                ? String.format("월 이익 %s × %s개월 × 계약 비율 %s", won(profit), trim(firstDeal), pct(m.getLeadToDealPct()))
                : String.format("이익 %s × 계약 비율 %s", won(profit), pct(m.getLeadToDealPct())), "계산"));
        basis.add(new Line("손익분기 CPA", won(valuePerLead), "문의 1건을 이보다 비싸게 사면 첫 계약만으로는 손해", "계산"));
        basis.add(new Line("목표 CPA(첫 계약 기준)", won(targetCpa),
                String.format("문의 1건의 가치 × 광고비 비율 %s", pct(m.getAdSharePct())), "계산"));
        if (ltvCpa > targetCpa) {
            basis.add(new Line("장기 가치 기준 상한", won(ltvCpa),
                    String.format("재계약까지 친 문의 1건의 가치 %s × 광고비 비율 %s. 참고용 상한", won(ltvValuePerLead),
                            pct(m.getAdSharePct())), "계산"));
        }
        basis.add(new Line("필요한 계약", String.format("월 %,d건", dealsNeeded), goalHow(m, revenue, profit), "계산"));
        basis.add(new Line("필요한 문의", String.format("월 %,d건", leadsNeeded),
                m.getGoalType() == GoalType.LEADS ? "목표로 정한 문의 수"
                        : String.format("계약 %,d건 ÷ 계약 비율 %s", dealsNeeded, pct(m.getLeadToDealPct())), "계산"));

        return new Result(Math.round(revenue), Math.round(profit), m.getLeadToDealPct(), repeat, valuePerLead,
                valuePerLead, targetCpa, dealsNeeded, leadsNeeded, leadsNeeded * targetCpa, basis, ltvValuePerLead, ltvCpa);
    }

    private static double average(List<Offering> offerings, boolean weighted,
            java.util.function.Function<Offering, Long> value) {
        if (!weighted) {
            return offerings.stream().mapToLong(value::apply).average().orElse(0);
        }
        double total = offerings.stream().mapToDouble(Offering::getSharePct).sum();
        return offerings.stream().mapToDouble(o -> value.apply(o) * o.getSharePct()).sum() / total;
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
