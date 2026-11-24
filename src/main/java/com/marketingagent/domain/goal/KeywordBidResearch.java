package com.marketingagent.domain.goal;

import com.marketingagent.domain.goal.BusinessModel.KeywordGroup;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 상담에서 나온 키워드 묶음을 네이버에 대 보고, 키워드마다 "이 순위에 걸면 얼마, 문의 1건에 얼마"를 붙인다.
 *
 * <p>쓰는 근거는 모두 네이버 응답이다. 월 검색량·경쟁 정도는 키워드도구, 1·3·5위 입찰가는 순위별 입찰가 추정,
 * 예상 클릭·비용은 3위 입찰가로 받은 성과 예측. 여기에 전환율을 곱해 문의 1건 비용(시장 CPA)을 낸다.
 * 이 클래스는 네이버 계정에 아무것도 쓰지 않는다.
 */
@Slf4j
@Component
public class KeywordBidResearch {

    static final int MAX_KEYWORDS = 15;
    private static final int MAX_SEEDS = 15;
    private static final int HINT_BATCH = 5;
    private static final long MIN_MONTHLY_SEARCHES = 30;
    private static final String BID_DEVICE = "MOBILE";
    private static final String ESTIMATE_DEVICE = "BOTH";
    private static final long MIN_BID = 70;
    private static final long MAX_BID = 100_000;
    private static final String RELATED_GROUP = "연관 검색어";

    /** 목표 CPA 이내 · 목표 초과지만 장기 상한 이내 · 장기 상한도 초과 · 예측 없음 */
    public static final String WITHIN = "WITHIN";
    public static final String OVER_TARGET = "OVER_TARGET";
    public static final String OVER_LTV = "OVER_LTV";
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * 키워드 한 건. 금액은 원, 클릭·비용은 한 달 예상.
     *
     * @param bid1 bid3 bid5   모바일 평균 1·3·5위에 필요한 추정 입찰가. 못 받으면 0
     * @param expectedCpc      3위 입찰가로 걸었을 때의 예상 클릭 단가
     * @param expectedCpa      예상 클릭 단가 ÷ 전환율. 문의 1건에 드는 광고비
     */
    public record KeywordBid(
            String keyword,
            String group,
            long monthlySearches,
            String competition,
            long bid1,
            long bid3,
            long bid5,
            long expectedClicks,
            long expectedCost,
            long expectedCpc,
            long expectedCpa,
            String verdict
    ) {}

    /**
     * 시장 시세와 목표 CPA 를 나란히 놓은 결과.
     *
     * @param marketCpa 키워드 전체를 3위로 걸었을 때 문의 1건에 드는 평균 광고비. 예측이 없으면 0
     */
    public record MarketCheck(
            long marketCpa,
            long targetCpa,
            long breakEvenCpa,
            long ltvCpa,
            double cvrPct,
            String cvrSource,
            String verdict,
            String explanation,
            List<KeywordBid> keywords,
            List<String> notes
    ) {}

    public MarketCheck research(SearchAdClient client, List<KeywordGroup> groups, double cvrPct, String cvrSource,
            GoalEconomics.Result econ) {
        List<String> notes = new ArrayList<>();
        Map<String, String> groupOf = seeds(groups);
        List<String> seeds = new ArrayList<>(groupOf.keySet());

        Map<String, RelatedKeyword> volume = new LinkedHashMap<>();
        List<RelatedKeyword> related = new ArrayList<>();
        try {
            for (int from = 0; from < seeds.size(); from += HINT_BATCH) {
                for (RelatedKeyword k : client.relatedKeywords(seeds.subList(from, Math.min(from + HINT_BATCH, seeds.size())))) {
                    volume.putIfAbsent(norm(k.keyword()), k);
                    related.add(k);
                }
            }
        } catch (RuntimeException e) {
            log.warn("키워드도구 조회 실패: {}", e.getMessage());
            notes.add("네이버 키워드도구에서 검색량을 받지 못했습니다. 검색량 없이 입찰가만 조사했습니다.");
        }

        // 상담에서 나온 키워드를 먼저 넣고, 남는 자리는 검색량이 많은 연관 검색어 중 상담 키워드를 품은 말로 채운다.
        Map<String, String> rows = new LinkedHashMap<>();
        seeds.forEach(s -> rows.put(s, groupOf.get(s)));
        related.stream()
                .filter(k -> k.totalCount() >= MIN_MONTHLY_SEARCHES && !rows.containsKey(norm(k.keyword())))
                .sorted(Comparator.comparingLong(RelatedKeyword::totalCount).reversed())
                .forEach(k -> {
                    if (rows.size() < MAX_KEYWORDS) {
                        String n = norm(k.keyword());
                        seeds.stream().filter(n::contains).findFirst()
                                .ifPresent(parent -> rows.putIfAbsent(n, groupOf.get(parent)));
                    }
                });
        List<String> keywords = rows.keySet().stream().limit(MAX_KEYWORDS).toList();
        if (keywords.isEmpty()) {
            return new MarketCheck(0, econ.targetCpa(), econ.breakEvenCpa(), econ.ltvCpa(), cvrPct, cvrSource, UNKNOWN,
                    "조사할 키워드가 없습니다.", List.of(), notes);
        }

        Map<String, Long> bid1 = bids(client, keywords, 1, notes);
        Map<String, Long> bid3 = bids(client, keywords, 3, notes);
        Map<String, Long> bid5 = bids(client, keywords, 5, notes);

        Map<String, PerformanceEstimate> perf = new LinkedHashMap<>();
        List<PerformanceEstimate.Query> queries = keywords.stream()
                .filter(k -> bid3.getOrDefault(k, 0L) > 0)
                .map(k -> new PerformanceEstimate.Query(k, clamp(bid3.get(k))))
                .toList();
        if (!queries.isEmpty()) {
            try {
                client.estimatePerformance(queries, ESTIMATE_DEVICE).forEach(p -> perf.put(norm(p.keyword()), p));
            } catch (RuntimeException e) {
                log.warn("성과 예측 실패: {}", e.getMessage());
                notes.add("네이버 성과 예측을 받지 못해 예상 클릭·비용이 비어 있습니다. 잠시 뒤 '다시 검토'를 눌러 주세요.");
            }
        }

        double cvr = Math.max(cvrPct, 0.01) / 100.0;
        List<KeywordBid> result = new ArrayList<>();
        long totalClicks = 0;
        long totalCost = 0;
        for (String k : keywords) {
            RelatedKeyword v = volume.get(k);
            PerformanceEstimate p = perf.get(k);
            long clicks = p == null ? 0 : p.clicks();
            long cost = p == null ? 0 : p.cost();
            long cpc = clicks > 0 ? Math.round(cost / (double) clicks) : 0;
            long cpa = cpc > 0 ? Math.round(cpc / cvr) : 0;
            if (clicks > 0) {
                totalClicks += clicks;
                totalCost += cost;
            }
            result.add(new KeywordBid(k, rows.get(k), v == null ? 0 : v.totalCount(), v == null ? "-" : v.competition(),
                    bid1.getOrDefault(k, 0L), bid3.getOrDefault(k, 0L), bid5.getOrDefault(k, 0L), clicks, cost, cpc, cpa,
                    verdict(cpa, econ)));
        }
        result.sort(Comparator.comparingInt((KeywordBid b) -> rank(b.verdict()))
                .thenComparingLong(b -> b.expectedCpa() == 0 ? Long.MAX_VALUE : b.expectedCpa()));

        long marketCpa = totalClicks == 0 ? 0 : Math.round(totalCost / (double) totalClicks / cvr);
        String verdict = verdict(marketCpa, econ);
        return new MarketCheck(marketCpa, econ.targetCpa(), econ.breakEvenCpa(), econ.ltvCpa(), cvrPct, cvrSource,
                verdict, explain(verdict, marketCpa, econ, cvrPct, result), result, notes);
    }

    static String verdict(long cpa, GoalEconomics.Result econ) {
        if (cpa <= 0) {
            return UNKNOWN;
        }
        if (cpa <= econ.targetCpa()) {
            return WITHIN;
        }
        return cpa <= Math.max(econ.ltvCpa(), econ.targetCpa()) ? OVER_TARGET : OVER_LTV;
    }

    private static String explain(String verdict, long market, GoalEconomics.Result econ, double cvrPct,
            List<KeywordBid> rows) {
        long within = rows.stream().filter(r -> WITHIN.equals(r.verdict())).count();
        String base = String.format("조사한 키워드를 평균 3위로 걸면, 클릭 단가와 전환율 %s로 따져 문의 1건에 약 %,d원이 듭니다. ",
                GoalEconomics.pct(cvrPct), market);
        return switch (verdict) {
            case WITHIN -> base + String.format("첫 계약 기준 목표 CPA %,d원 안이라 지금 시세로도 남습니다.", econ.targetCpa());
            case OVER_TARGET -> base + String.format("첫 계약 기준 목표 CPA %,d원보다 비싸서 첫 계약만으로는 남지 않고, "
                            + "재계약까지 쳐야(상한 %,d원) 남습니다. 목표 안에 드는 키워드 %d개부터 쓰고, 나머지는 4~5위로 낮추거나 "
                            + "문의 전환율을 올린 뒤 늘리는 편이 안전합니다.", econ.targetCpa(), econ.ltvCpa(), within);
            case OVER_LTV -> base + String.format("재계약까지 친 상한 %,d원보다도 비쌉니다. 넓은 키워드는 빼고 "
                            + "구매 의도가 분명한 세부 키워드(견적·조건·가격 등)와 랜딩 페이지 개선부터 하는 것을 권합니다.",
                    Math.max(econ.ltvCpa(), econ.targetCpa()));
            default -> "네이버 예측을 받지 못해 시장 시세를 계산하지 못했습니다.";
        };
    }

    private static int rank(String verdict) {
        return switch (verdict) {
            case WITHIN -> 0;
            case OVER_TARGET -> 1;
            case OVER_LTV -> 2;
            default -> 3;
        };
    }

    private Map<String, Long> bids(SearchAdClient client, List<String> keywords, int position, List<String> notes) {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            client.estimateBidForPosition(keywords, position, BID_DEVICE).forEach((k, v) -> result.put(norm(k), v));
        } catch (RuntimeException e) {
            log.warn("{}위 입찰가 추정 실패: {}", position, e.getMessage());
            notes.add(position + "위 입찰가를 받지 못했습니다.");
        }
        return result;
    }

    /** 묶음의 키워드를 공백 없는 네이버 표기로 바꾸고 어느 묶음인지 기억한다. */
    static Map<String, String> seeds(List<KeywordGroup> groups) {
        Map<String, String> result = new LinkedHashMap<>();
        if (groups == null) {
            return result;
        }
        for (KeywordGroup g : groups) {
            if (g.getKeywords() == null) {
                continue;
            }
            for (String k : g.getKeywords()) {
                String n = norm(k);
                if (!n.isEmpty() && result.size() < MAX_SEEDS) {
                    result.putIfAbsent(n, g.getName() == null || g.getName().isBlank() ? RELATED_GROUP : g.getName());
                }
            }
        }
        return result;
    }

    static String norm(String keyword) {
        return keyword == null ? "" : keyword.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static long clamp(long bid) {
        return Math.max(MIN_BID, Math.min(MAX_BID, Math.round(bid / 10.0) * 10));
    }
}
