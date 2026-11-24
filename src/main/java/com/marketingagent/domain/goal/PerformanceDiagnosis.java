package com.marketingagent.domain.goal;

import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.KeywordStatHourlyRepository;
import com.marketingagent.domain.ads.ReportAnalyticsService;
import com.marketingagent.domain.ads.SearchQueryDailyRepository;
import com.marketingagent.domain.ads.dto.HourOfDayPerformance;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.dto.SearchQueryAggregate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이미 광고를 하던 계정의 최근 실적을 진단한다. 문제점·개선점은 모두 금액·건수 근거와 함께 낸다.
 *
 * <p>보는 것: 전환이 잡히는지(추적), 전환 없이 돈만 쓴 키워드, 목표 CPA 를 크게 넘는 키워드, 효율이 좋은데 덜 쓰는 키워드,
 * 몇 개 키워드에 쏠린 예산, 돈만 쓰고 전환이 없는 시간대, 그리고 유입 검색어에서 찾은 새 키워드 후보와 제외 키워드 후보.
 * 저장된 실적만 읽고 네이버에는 아무것도 쓰지 않는다. 실적이 적으면 판단하지 않고 그렇다고 말한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceDiagnosis {

    static final int WINDOW_DAYS = 30;
    static final long MIN_CLICKS = 30;
    static final long TRACKING_CHECK_CLICKS = 300;
    static final long MIN_WASTE_COST = 10_000;
    static final long MIN_QUERY_CLICKS = 3;
    static final long MIN_NEGATIVE_COST = 3_000;

    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final SearchQueryDailyRepository searchQueryRepository;
    private final ReportAnalyticsService reportAnalytics;
    private final KeywordStatHourlyRepository hourlyRepository;

    /**
     * 진단 한 줄.
     *
     * @param evidence 근거 숫자를 담은 설명
     * @param action   무엇을 하면 되는지
     * @param amount   관련 금액(원). 없으면 0
     * @param keywords 해당 키워드·시간대 등 대상
     */
    public record Finding(String severity, String title, String evidence, String action, long amount, List<String> keywords) {}

    /** 유입 검색어에서 찾은 새 키워드 후보 */
    public record KeywordIdea(String keyword, long clicks, long cost, String reason) {}

    public record Result(
            boolean hasData,
            LocalDate since,
            LocalDate until,
            long cost,
            long clicks,
            long conversions,
            long accountCpa,
            long targetCpa,
            List<Finding> problems,
            List<Finding> improvements,
            List<KeywordIdea> newKeywords,
            List<String> negativeCandidates,
            List<String> notes
    ) {}

    public Result diagnose(Long account, long targetCpa) {
        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(WINDOW_DAYS - 1L);
        List<String> notes = new ArrayList<>();
        Map<String, AdKeyword> keywords = keywordRepository.findByAdAccountId(account).stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, k -> k, (a, b) -> a));
        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(account, since, until);

        long cost = rows.stream().mapToLong(r -> nz(r.salesAmt())).sum();
        long clicks = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long conversions = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();
        long accountCpa = conversions == 0 ? 0 : cost / conversions;
        List<Finding> problems = new ArrayList<>();
        List<Finding> improvements = new ArrayList<>();

        if (clicks < MIN_CLICKS) {
            notes.add(String.format("최근 %d일 클릭이 %,d회뿐이라 실적 진단은 건너뛰었습니다. 광고를 운영하면서 실적이 쌓이면 다시 진단합니다.",
                    WINDOW_DAYS, clicks));
            return new Result(false, since, until, cost, clicks, conversions, accountCpa, targetCpa, problems, improvements,
                    List.of(), List.of(), notes);
        }

        boolean tracking = conversions > 0;
        if (!tracking && clicks >= TRACKING_CHECK_CLICKS) {
            problems.add(new Finding(HIGH, "전환이 한 건도 잡히지 않습니다",
                    String.format("최근 %d일 클릭 %,d회, 광고비 %s 동안 네이버에 잡힌 전환이 0건입니다.", WINDOW_DAYS, clicks, won(cost)),
                    "홈페이지에 네이버 전환 추적(프리미엄 로그분석 전환 스크립트)이 문의·전화 버튼에 걸려 있는지부터 확인하세요. "
                            + "추적이 없으면 어떤 키워드가 돈을 버는지 알 수 없고 자동 입찰도 판단을 못 합니다.", cost, List.of()));
        }

        if (tracking) {
            if (targetCpa > 0 && accountCpa > targetCpa) {
                problems.add(new Finding(accountCpa > targetCpa * 2 ? HIGH : MEDIUM, "문의 1건 비용이 목표보다 비쌉니다",
                        String.format("최근 %d일 전환 %,d건에 광고비 %s를 써서 문의 1건에 %s이 들었습니다. 첫 계약 기준 목표 CPA는 %s입니다.",
                                WINDOW_DAYS, conversions, won(cost), won(accountCpa), won(targetCpa)),
                        "아래의 전환 없는 키워드와 비싼 키워드부터 정리하면 평균이 내려갑니다.", cost - conversions * targetCpa, List.of()));
            }

            long wasteFloor = Math.max(MIN_WASTE_COST, targetCpa);
            List<KeywordAggregate> waste = rows.stream()
                    .filter(r -> nz(r.ccnt()) == 0 && nz(r.salesAmt()) >= wasteFloor)
                    .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                    .toList();
            if (!waste.isEmpty()) {
                long wasteCost = waste.stream().mapToLong(r -> nz(r.salesAmt())).sum();
                double share = wasteCost * 100.0 / Math.max(1, cost);
                problems.add(new Finding(share >= 20 ? HIGH : MEDIUM, "전환 없이 광고비만 쓴 키워드가 있습니다",
                        String.format("키워드 %d개가 최근 %d일 동안 전환 0건으로 %s(전체 광고비의 %.0f%%)를 썼습니다.",
                                waste.size(), WINDOW_DAYS, won(wasteCost), share),
                        "이 키워드들은 끄거나 입찰가를 크게 낮추세요. 검색 의도가 맞는데 전환이 없다면 광고가 열어 주는 페이지를 점검하세요.",
                        wasteCost, names(waste, keywords, 10)));
            }

            List<KeywordAggregate> expensive = rows.stream()
                    .filter(r -> nz(r.ccnt()) > 0 && targetCpa > 0 && nz(r.salesAmt()) / nz(r.ccnt()) > targetCpa * 1.5)
                    .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                    .toList();
            if (!expensive.isEmpty()) {
                long over = expensive.stream().mapToLong(r -> nz(r.salesAmt()) - nz(r.ccnt()) * targetCpa).sum();
                problems.add(new Finding(MEDIUM, "목표 CPA 를 크게 넘는 키워드가 있습니다",
                        String.format("키워드 %d개가 문의 1건에 목표(%s)의 1.5배 넘게 썼습니다. 목표대로 샀다면 %s을 아낄 수 있었습니다.",
                                expensive.size(), won(targetCpa), won(over)),
                        "입찰가를 내려 순위를 4~5위로 낮추세요. 순위를 조금 낮춰도 문의 수는 덜 줄고 비용은 크게 줄어드는 경우가 많습니다.",
                        over, names(expensive, keywords, 10)));
            }

            List<KeywordAggregate> winners = rows.stream()
                    .filter(r -> nz(r.ccnt()) >= 2 && targetCpa > 0 && nz(r.salesAmt()) / nz(r.ccnt()) <= targetCpa * 0.7)
                    .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.ccnt())).reversed())
                    .toList();
            if (!winners.isEmpty()) {
                long winConv = winners.stream().mapToLong(r -> nz(r.ccnt())).sum();
                long winCost = winners.stream().mapToLong(r -> nz(r.salesAmt())).sum();
                improvements.add(new Finding(MEDIUM, "목표보다 싸게 문의를 사는 키워드에 더 쓰세요",
                        String.format("키워드 %d개가 전환 %,d건을 문의 1건 %s에 샀습니다(목표 %s).", winners.size(), winConv,
                                won(winCost / Math.max(1, winConv)), won(targetCpa)),
                        "이 키워드들은 입찰가를 10~20% 올리거나 일예산이 일찍 떨어지지 않게 늘려 노출을 더 받으세요.",
                        winCost, names(winners, keywords, 10)));
            }
        }

        if (rows.size() >= 10) {
            long top3 = rows.stream().mapToLong(r -> nz(r.salesAmt())).boxed().sorted(Comparator.reverseOrder())
                    .limit(3).mapToLong(Long::longValue).sum();
            double share = top3 * 100.0 / Math.max(1, cost);
            if (share >= 70) {
                improvements.add(new Finding(LOW, "광고비가 몇 개 키워드에 쏠려 있습니다",
                        String.format("상위 3개 키워드가 광고비의 %.0f%%를 씁니다. 경쟁이 심한 넓은 키워드일수록 단가가 비쌉니다.", share),
                        "의도가 분명한 세부 키워드(지역·조건·견적 등)를 늘려 같은 돈으로 더 싼 문의를 사세요.", top3, List.of()));
            }
        }

        hourFinding(account, since, until, conversions).ifPresent(problems::add);

        List<KeywordIdea> ideas = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        searchQueries(account, since, until, keywords.values(), ideas, negatives, notes);
        if (!ideas.isEmpty()) {
            improvements.add(new Finding(MEDIUM, "실제로 들어온 검색어 중 키워드로 등록 안 된 것이 있습니다",
                    String.format("등록하지 않은 검색어 %d개로 클릭이 꾸준히 들어왔습니다(합계 %,d회).", ideas.size(),
                            ideas.stream().mapToLong(KeywordIdea::clicks).sum()),
                    "따로 키워드로 등록하면 그 검색어에 맞는 문구와 입찰가를 따로 줄 수 있습니다. 아래 입찰가 표에 함께 넣어 두었습니다.",
                    ideas.stream().mapToLong(KeywordIdea::cost).sum(), ideas.stream().map(KeywordIdea::keyword).toList()));
        }
        if (!negatives.isEmpty()) {
            problems.add(new Finding(MEDIUM, "업종과 관계없어 보이는 검색어로도 광고비가 나갔습니다",
                    String.format("등록 키워드와 겹치는 말이 없는 검색어 %d개에 광고비가 쓰였습니다.", negatives.size()),
                    "실제로 관계없는 검색어인지 확인하고 제외 키워드로 등록하세요.", 0, negatives));
        }

        problems.sort(Comparator.comparingInt((Finding f) -> rank(f.severity())).thenComparing(f -> -f.amount()));
        return new Result(true, since, until, cost, clicks, conversions, accountCpa, targetCpa, problems, improvements,
                ideas, negatives, notes);
    }

    private java.util.Optional<Finding> hourFinding(Long account, LocalDate since, LocalDate until, long conversions) {
        if (conversions < 10) {
            return java.util.Optional.empty();
        }
        // hourOfDay 는 데이터가 없으면 트랜잭션 안에서 예외를 던져 바깥 트랜잭션까지 롤백시키므로, 먼저 있는지 본다.
        if (hourlyRepository.aggregateByHour(account, since, until).isEmpty()) {
            return java.util.Optional.empty();
        }
        List<HourOfDayPerformance> hours = reportAnalytics.hourOfDay(account, since, until, null);
        long hourCost = hours.stream().mapToLong(HourOfDayPerformance::salesAmt).sum();
        if (hourCost == 0 || hours.stream().mapToLong(HourOfDayPerformance::convCnt).sum() == 0) {
            return java.util.Optional.empty();
        }
        List<HourOfDayPerformance> dead = hours.stream()
                .filter(h -> h.convCnt() == 0 && h.salesAmt() >= hourCost * 0.04)
                .toList();
        if (dead.isEmpty()) {
            return java.util.Optional.empty();
        }
        long deadCost = dead.stream().mapToLong(HourOfDayPerformance::salesAmt).sum();
        List<String> labels = dead.stream().map(h -> h.hour() + "시").toList();
        return java.util.Optional.of(new Finding(MEDIUM, "광고비만 나가고 전환이 없는 시간대가 있습니다",
                String.format("%s에 광고비 %s(시간대 합계의 %.0f%%)를 썼지만 전환은 0건이었습니다.", String.join(", ", labels),
                        won(deadCost), deadCost * 100.0 / hourCost),
                "이 시간대는 입찰가를 낮추거나(시간대별 입찰 가중치) 광고를 쉬게 하세요.", deadCost, labels));
    }

    private void searchQueries(Long account, LocalDate since, LocalDate until, java.util.Collection<AdKeyword> registered,
            List<KeywordIdea> ideas, List<String> negatives, List<String> notes) {
        List<SearchQueryAggregate> rows = searchQueryRepository.aggregate(account, since, until);
        if (rows.isEmpty()) {
            notes.add("유입 검색어 리포트가 없어 새 키워드·제외 키워드 후보는 찾지 못했습니다. '검색어 리포트 가져오기'를 하면 함께 진단합니다.");
            return;
        }
        Set<String> names = registered.stream().map(k -> KeywordBidResearch.norm(k.getKeyword())).collect(Collectors.toSet());
        Set<String> grams = new HashSet<>();
        names.forEach(n -> grams.addAll(bigrams(n)));
        for (SearchQueryAggregate q : rows) {
            String n = KeywordBidResearch.norm(q.searchQuery());
            if (n.isEmpty() || names.contains(n)) {
                continue;
            }
            long overlap = bigrams(n).stream().filter(grams::contains).count();
            if (overlap >= 2 && nz(q.clkCnt()) >= MIN_QUERY_CLICKS && ideas.size() < 10) {
                ideas.add(new KeywordIdea(q.searchQuery(), nz(q.clkCnt()), nz(q.salesAmt()),
                        String.format("최근 %d일 클릭 %,d회 · 광고비 %s", WINDOW_DAYS, nz(q.clkCnt()), won(nz(q.salesAmt())))));
            } else if (overlap == 0 && nz(q.salesAmt()) >= MIN_NEGATIVE_COST && negatives.size() < 10) {
                negatives.add(q.searchQuery());
            }
        }
    }

    static Set<String> bigrams(String s) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 2 <= s.length(); i++) {
            result.add(s.substring(i, i + 2));
        }
        return result;
    }

    private static List<String> names(List<KeywordAggregate> rows, Map<String, AdKeyword> keywords, int limit) {
        return rows.stream().map(r -> keywords.get(r.nccKeywordId())).filter(java.util.Objects::nonNull)
                .map(AdKeyword::getKeyword).distinct().limit(limit).toList();
    }

    private static int rank(String severity) {
        return HIGH.equals(severity) ? 0 : MEDIUM.equals(severity) ? 1 : 2;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }
}
