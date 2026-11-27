package com.marketingagent.domain.ads.launch;

import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 키워드를 데이터로 고르고 입찰가를 정한다. 네이버 호출은 하지 않고 받은 숫자로 계산만 한다.
 *
 * <ol>
 *   <li>관련성: 키워드도구 후보 중 상품 말(렌트·리스 등)과 집중 말(레이·저신용 등)이 함께 든 것만, 제외 말이 든 것은 뺀다.</li>
 *   <li>전환 가능성 가중치: 고객층 말(저신용·회생 등)이나 정확한 상품 말(레이)이 들어가면 전환이 잘 되는 검색으로 본다.</li>
 *   <li>입찰가: 입찰가 단계별 예상 클릭·비용(네이버 성과 예측)으로, 한 달 예산 안에서 "광고비 1원당 늘어나는 가중 클릭"이
 *       가장 큰 키워드·단계부터 채운다. 클릭 1회 최대 금액과 손익분기를 넘는 단계는 쓰지 않는다.</li>
 * </ol>
 */
public final class KeywordSelector {

    /**
     * @param audienceTerms 고객층 말. 들어가면 가중치 audienceWeight
     * @param productTerms  정확한 상품 말. 들어가면 가중치 productWeight (곱해진다)
     */
    public record Rules(List<String> requireAny, List<String> focusAny, List<String> excludeWords,
                        List<String> audienceTerms, List<String> productTerms, double audienceWeight,
                        double productWeight, long minMonthlySearches) {}

    /**
     * @param monthlyBudget 한 달 광고비 상한
     * @param maxCpc        클릭 1회 최대 금액
     * @param breakEvenCpa  전환 1건으로 남는 돈. 0 이면 확인하지 않는다
     * @param cvr           가정 전환율
     */
    public record Budget(long monthlyBudget, long maxCpc, long breakEvenCpa, double cvr, int maxKeywords) {}

    /** 키워드 하나의 분석 결과. bid 가 0 이면 넣지 않는다(reason 에 이유). 클릭·비용은 한 달 예상. */
    public record Pick(String keyword, long monthlySearches, double ctrPct, String competition, double weight,
                       long bid, long clicks, long cost, String reason, String tag) {
        /** 추천 · 최대 금액 안에서 클릭 없음 · 검색 적음 · 효율 낮음 */
        public static final String PICK = "PICK";
        public static final String OVER_CAP = "OVER_CAP";
        public static final String LOW_VOLUME = "LOW_VOLUME";
        public static final String LOW_EFFICIENCY = "LOW_EFFICIENCY";

        public long cpc() {
            return clicks > 0 ? Math.round(cost / (double) clicks) : 0;
        }
    }

    private KeywordSelector() {}

    public static boolean relevant(String keyword, Rules rules) {
        String k = keyword.toLowerCase(Locale.ROOT);
        return containsAny(k, rules.requireAny()) && containsAny(k, rules.focusAny())
                && (rules.excludeWords() == null || rules.excludeWords().isEmpty()
                        || !containsAny(k, rules.excludeWords()));
    }

    public static double weight(String keyword, Rules rules) {
        String k = keyword.toLowerCase(Locale.ROOT);
        double w = 1.0;
        if (containsAny(k, rules.audienceTerms())) {
            w *= rules.audienceWeight();
        }
        if (containsAny(k, rules.productTerms())) {
            w *= rules.productWeight();
        }
        return w;
    }

    /** 키워드도구 월평균 클릭수 ÷ 월 검색수. 검색수가 없으면 0. */
    public static double ctrPct(RelatedKeyword r) {
        long searches = r.totalCount();
        return searches > 0 ? (r.pcClick() + r.mobileClick()) * 100.0 / searches : 0;
    }

    /**
     * @param stats     후보 키워드 → 키워드도구 결과
     * @param estimates 후보 키워드 → 입찰가 단계별 예측(입찰가 오름차순이 아니어도 된다)
     */
    public static List<Pick> select(Map<String, RelatedKeyword> stats, Map<String, List<PerformanceEstimate>> estimates,
            Rules rules, Budget budget) {
        Map<String, List<PerformanceEstimate>> curves = new HashMap<>();
        Map<String, String> rejected = new HashMap<>();
        Map<String, String> rejectedTag = new HashMap<>();
        for (Map.Entry<String, RelatedKeyword> e : stats.entrySet()) {
            String keyword = e.getKey();
            if (e.getValue().totalCount() < rules.minMonthlySearches()) {
                rejected.put(keyword, String.format("월 검색 %d회로 너무 적어 뺍니다.", e.getValue().totalCount()));
                rejectedTag.put(keyword, Pick.LOW_VOLUME);
                continue;
            }
            List<PerformanceEstimate> curve = usableCurve(estimates.getOrDefault(keyword, List.of()), budget);
            if (curve.isEmpty()) {
                rejected.put(keyword, String.format("클릭 1회 최대 %,d원 안에서는 예상 클릭이 없어 뺍니다.", budget.maxCpc()));
                rejectedTag.put(keyword, Pick.OVER_CAP);
                continue;
            }
            curves.put(keyword, curve);
        }

        // 그리디 배분: 지금 단계에서 다른 단계로 올릴 때 "1원당 가중 클릭"이 가장 큰 것을 고른다
        Map<String, Integer> level = new HashMap<>();
        long spent = 0;
        while (true) {
            String bestKeyword = null;
            int bestLevel = -1;
            double bestEfficiency = 0;
            long bestDelta = 0;
            for (Map.Entry<String, List<PerformanceEstimate>> e : curves.entrySet()) {
                String keyword = e.getKey();
                int now = level.getOrDefault(keyword, -1);
                if (now < 0 && level.size() >= budget.maxKeywords()) {
                    continue;
                }
                List<PerformanceEstimate> curve = e.getValue();
                long nowClicks = now < 0 ? 0 : curve.get(now).clicks();
                long nowCost = now < 0 ? 0 : curve.get(now).cost();
                double w = weight(keyword, rules);
                for (int next = now + 1; next < curve.size(); next++) {
                    long dClicks = curve.get(next).clicks() - nowClicks;
                    long dCost = curve.get(next).cost() - nowCost;
                    if (dClicks <= 0 || dCost <= 0 || spent + dCost > budget.monthlyBudget()) {
                        continue;
                    }
                    // 이 단계로 올려 더 사는 클릭이 손익분기를 넘으면 쓰지 않는다
                    double marginalCpa = dCost / (dClicks * w * budget.cvr());
                    if (budget.breakEvenCpa() > 0 && marginalCpa > budget.breakEvenCpa()) {
                        continue;
                    }
                    double efficiency = dClicks * w / dCost;
                    if (efficiency > bestEfficiency) {
                        bestEfficiency = efficiency;
                        bestKeyword = keyword;
                        bestLevel = next;
                        bestDelta = dCost;
                    }
                }
            }
            if (bestKeyword == null) {
                break;
            }
            level.put(bestKeyword, bestLevel);
            spent += bestDelta;
        }

        List<Pick> picks = new ArrayList<>();
        for (Map.Entry<String, RelatedKeyword> e : stats.entrySet()) {
            String keyword = e.getKey();
            RelatedKeyword r = e.getValue();
            double w = weight(keyword, rules);
            Integer at = level.get(keyword);
            if (at != null) {
                PerformanceEstimate p = curves.get(keyword).get(at);
                picks.add(new Pick(keyword, r.totalCount(), ctrPct(r), r.competition(), w, p.bid(), p.clicks(),
                        p.cost(), String.format("월 검색 %,d회·클릭률 %.1f%%·경쟁 %s. %,d원에 월 %,d클릭·%,d원(클릭당 %,d원) 예상, "
                                        + "전환 가중치 %.1f로 1원당 효율이 높아 넣습니다.",
                                r.totalCount(), ctrPct(r), r.competition(), p.bid(), p.clicks(), p.cost(),
                                p.clicks() > 0 ? p.cost() / p.clicks() : 0, w), Pick.PICK));
            } else {
                String why = rejected.getOrDefault(keyword, "예산 안에서 다른 키워드보다 광고비 대비 클릭 효율이 낮아 뺍니다.");
                picks.add(new Pick(keyword, r.totalCount(), ctrPct(r), r.competition(), w, 0, 0, 0, why,
                        rejectedTag.getOrDefault(keyword, Pick.LOW_EFFICIENCY)));
            }
        }
        picks.sort(Comparator.comparingLong(Pick::bid).reversed().thenComparing(Pick::clicks, Comparator.reverseOrder()));
        return picks;
    }

    /** 최대 금액 이하 입찰가만, 입찰가 순으로, 클릭·비용이 줄지 않게 정리한 곡선. */
    static List<PerformanceEstimate> usableCurve(List<PerformanceEstimate> raw, Budget budget) {
        List<PerformanceEstimate> sorted = raw.stream()
                .filter(p -> p.bid() <= budget.maxCpc() && p.clicks() > 0 && p.cost() > 0)
                .sorted(Comparator.comparingLong(PerformanceEstimate::bid))
                .toList();
        List<PerformanceEstimate> curve = new ArrayList<>();
        for (PerformanceEstimate p : sorted) {
            PerformanceEstimate last = curve.isEmpty() ? null : curve.get(curve.size() - 1);
            // 입찰가를 올려도 클릭이 안 늘면 그 단계는 돈만 더 쓴다
            if (last == null || p.clicks() > last.clicks()) {
                curve.add(p);
            }
        }
        return curve;
    }

    private static boolean containsAny(String keyword, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return true;
        }
        for (String t : terms) {
            if (keyword.contains(t.toLowerCase(Locale.ROOT).replace(" ", ""))) {
                return true;
            }
        }
        return false;
    }
}
