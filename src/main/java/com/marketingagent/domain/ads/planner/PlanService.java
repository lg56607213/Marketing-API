package com.marketingagent.domain.ads.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.EfficiencyMath;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.planner.PlannerDtos.Basis;
import com.marketingagent.domain.ads.planner.PlannerDtos.GoalType;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanKeyword;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanRequest;
import com.marketingagent.domain.ads.planner.PlannerDtos.Scenario;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표(월 전환수 또는 월 예산)를 받아 키워드·입찰가·예산·기대 성과가 담긴 운영 계획을 제안한다.
 *
 * <p>숫자는 가능한 한 가정이 아니라 근거에서 가져온다.
 * <ul>
 *   <li>클릭수·클릭 단가: 네이버 성과 예측을 키워드마다 입찰가 4단계로 받아, 입찰가를 올릴 때 클릭이 얼마나 늘고
 *       비용이 얼마나 드는지를 본다. 추가 클릭이 가장 싼 단계부터 사들여 목표나 예산에 닿으면 멈춘다.</li>
 *   <li>전환율: 내 광고 실적 → 내 사이트 실측(방문자·문의 건수) → 가정 순으로 쓰고, 실측이면 90% 신뢰구간을 범위로 낸다.</li>
 *   <li>수익성: 전환 1건의 매출을 알면 수익이 남는 클릭 단가 한도를 계산해, 그보다 비싼 클릭은 사지 않는다.</li>
 * </ul>
 * 어떤 숫자가 실측이고 어떤 숫자가 예측·가정인지는 제안의 basis 에 적는다. 이 서비스는 네이버 계정에 아무것도 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    static final double DEFAULT_CVR_PCT = 2.0;
    static final String GRADE_MEASURED = "실측";
    static final String GRADE_ESTIMATE = "네이버 예측";
    static final String GRADE_ASSUMED = "가정";

    private static final int TARGET_POSITION = 3;
    private static final String BID_DEVICE = "MOBILE";
    private static final String ESTIMATE_DEVICE = "BOTH";
    /** 목표 순위 추정 입찰가 대비 몇 배의 입찰가로 예측을 받아 볼지. */
    private static final double[] BID_LADDER = {0.4, 0.7, 1.0, 1.3};
    private static final int MAX_CANDIDATES = 20;
    private static final long MIN_MONTHLY_SEARCHES = 30;
    private static final long MIN_CLICKS_FOR_ACTUAL_CVR = 100;
    private static final long MIN_SITE_VISITORS = 100;
    private static final long MIN_MONTHLY_BUDGET = 10_000;

    private final SearchAdClient searchAdClient;
    private final KeywordStatDailyRepository statRepository;
    private final MarketingPlanRepository planRepository;
    private final BidPolicyProperties bidPolicy;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlanProposal propose(PlanRequest request) {
        List<String> seeds = request.seedKeywords().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (seeds.isEmpty()) {
            throw new BadRequestException("핵심 키워드를 1개 이상 입력하세요.");
        }
        boolean byConversions = request.goalType() == GoalType.CONVERSIONS;
        if (byConversions && (request.targetConversions() == null || request.targetConversions() <= 0)) {
            throw new BadRequestException("월 목표 전환수를 1 이상으로 입력하세요.");
        }
        if (!byConversions && (request.monthlyBudget() == null || request.monthlyBudget() < MIN_MONTHLY_BUDGET)) {
            throw new BadRequestException("월 예산을 " + MIN_MONTHLY_BUDGET + "원 이상으로 입력하세요.");
        }

        List<String> assumptions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Basis> basis = new ArrayList<>();

        Cvr cvr = resolveCvr(request, warnings);
        basis.add(new Basis("전환율", String.format("%.2f%% (범위 %.2f%% ~ %.2f%%)", cvr.pct(), cvr.lowPct(), cvr.highPct()),
                cvr.source(), cvr.grade()));

        Long maxCpc = maxCpc(request, cvr, basis);

        List<Candidate> candidates = findCandidates(seeds, warnings, basis);
        if (candidates.isEmpty()) {
            throw new BadRequestException("입력한 키워드로는 검색량이 있는 후보를 찾지 못했습니다. 더 일반적인 키워드로 다시 시도하세요.");
        }

        long budgetCap = byConversions ? Long.MAX_VALUE : request.monthlyBudget();
        long clicksNeeded = byConversions
                ? (long) Math.ceil(request.targetConversions() / (cvr.pct() / 100.0))
                : Long.MAX_VALUE;

        Allocation allocation = allocate(candidates, clicksNeeded, budgetCap, maxCpc);
        long clicks = allocation.clicks();
        long cost = allocation.cost();
        if (clicks == 0) {
            throw new BadRequestException(maxCpc != null && allocation.blockedByMaxCpc()
                    ? String.format("수익이 남는 클릭 단가 한도(%,d원) 안에서 살 수 있는 클릭이 없습니다. "
                            + "전환 1건의 매출이나 목표 수익률을 다시 확인하세요.", maxCpc)
                    : "이 예산으로는 가장 싼 키워드의 클릭도 살 수 없습니다. 예산을 늘려 주세요.");
        }

        List<PlanKeyword> keywords = new ArrayList<>();
        for (Candidate c : candidates) {
            Point chosen = allocation.chosen().get(c.keyword());
            Point shown = chosen != null ? chosen : c.reference();
            keywords.add(new PlanKeyword(c.keyword(), c.searches(), c.competition(), shown.bid(),
                    chosen != null ? chosen.clicks() : 0, chosen != null ? chosen.cost() : 0, chosen != null,
                    chosen != null ? chosen.impressions() : 0,
                    shown.clicks() > 0 ? shown.cost() / shown.clicks() : 0));
        }

        // 표는 계획에 넣은 키워드를 비용이 큰 순으로 먼저 보여 준다.
        keywords.sort(Comparator.comparing((PlanKeyword k) -> !k.selected())
                .thenComparing(Comparator.comparingLong(PlanKeyword::expectedCost).reversed()));

        long expectedConversions = Math.round(clicks * cvr.pct() / 100.0);
        if (byConversions && clicks < clicksNeeded) {
            warnings.add(String.format(
                    "목표 %,d건에 못 미칩니다. %s 기대할 수 있는 전환은 월 %,d건 안팎입니다. 핵심 키워드를 넓히거나 목표를 낮추세요.",
                    request.targetConversions(),
                    allocation.blockedByMaxCpc()
                            ? String.format("수익이 남는 클릭 단가 한도(%,d원)를 넘는 클릭은 사지 않았기 때문입니다. 이 한도 안에서", maxCpc)
                            : "후보 키워드의 예상 클릭이 모자랍니다. 이 키워드들로",
                    expectedConversions));
        }

        long monthlyBudget = byConversions ? roundUp(cost, 10_000) : request.monthlyBudget();
        long dailyBudget = roundUp((long) Math.ceil(monthlyBudget / 30.0), 1_000);
        long targetCpa = expectedConversions == 0 ? 0 : cost / expectedConversions;
        long targetConversions = byConversions ? request.targetConversions() : expectedConversions;

        assumptions.add("예상 클릭·비용은 네이버가 최근 실적으로 계산한 예측입니다. 계절과 경쟁 상황에 따라 달라지며, 보통 첫 2주 실적으로 보정합니다.");
        assumptions.add("키워드마다 입찰가를 4단계로 예측받아, 추가 클릭 한 번이 가장 싼 단계부터 골랐습니다. "
                + "그래서 표의 입찰가는 키워드마다 다르고, 비싼 키워드는 낮은 순위로 시작합니다.");
        if (!GRADE_MEASURED.equals(cvr.grade())) {
            assumptions.add("전환율은 실측이 아닙니다. 이 계획에서 가장 불확실한 숫자이므로 아래 시나리오의 범위로 읽으세요.");
        }

        PlanProposal draft = new PlanProposal(null, request.goalType(), seeds, monthlyBudget, dailyBudget, clicks,
                expectedConversions, targetCpa, round2(cvr.pct()), cvr.source(),
                scenarios(clicks, cost, cvr, request.saleValue()), keywords, assumptions,
                steps(dailyBudget, targetCpa, keywords), warnings, LocalDateTime.now(),
                round2(cvr.lowPct()), round2(cvr.highPct()), maxCpc, basis);

        MarketingPlan saved = planRepository.save(new MarketingPlan(request.goalType().name(), monthlyBudget,
                targetConversions, targetCpa, toJson(draft)));
        log.info("운영 계획 제안: 목표 {} / 월 {}원 / 기대 전환 {}건 / 키워드 {}개 / 전환율 근거 {}",
                request.goalType(), monthlyBudget, expectedConversions, allocation.chosen().size(), cvr.grade());
        return withId(draft, saved.getId());
    }

    @Transactional(readOnly = true)
    public Optional<PlanProposal> current() {
        return planRepository.findTopByOrderByIdDesc().map(plan -> withId(fromJson(plan.getProposalJson()), plan.getId()));
    }

    // --- 전환율 ---

    /** 내 광고 실적 → 내 사이트 실측 → 가정 순으로 전환율을 정한다. */
    private Cvr resolveCvr(PlanRequest request, List<String> warnings) {
        LocalDate until = LocalDate.now().minusDays(1);
        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(until.minusDays(29), until);
        long clicks = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long conversions = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();

        if (clicks >= MIN_CLICKS_FOR_ACTUAL_CVR && conversions > 0) {
            double[] range = EfficiencyMath.wilson(conversions, clicks, EfficiencyMath.Z90);
            return new Cvr(conversions * 100.0 / clicks, range[0] * 100, range[1] * 100,
                    String.format("내 광고의 최근 30일 실적: 클릭 %,d회 중 전환 %,d건", clicks, conversions), GRADE_MEASURED);
        }
        if (clicks >= MIN_CLICKS_FOR_ACTUAL_CVR) {
            warnings.add(String.format(
                    "최근 30일에 광고 클릭이 %,d회 있었지만 전환이 한 건도 잡히지 않았습니다. 사이트에 전환 추적이 설치돼 있는지 먼저 확인하세요. "
                            + "추적이 없으면 어떤 계획도 검증할 수 없습니다.", clicks));
        }

        Long visitors = request.siteMonthlyVisitors();
        Long siteConversions = request.siteMonthlyConversions();
        if (visitors != null && siteConversions != null && visitors >= MIN_SITE_VISITORS
                && siteConversions > 0 && siteConversions <= visitors) {
            double[] range = EfficiencyMath.wilson(siteConversions, visitors, EfficiencyMath.Z90);
            warnings.add("전환율은 사이트 전체 방문자 기준입니다. 검색광고로 들어온 방문자는 목적이 뚜렷해 이보다 높게 나오는 경우가 많지만, "
                    + "확인되기 전까지는 이 값을 씁니다.");
            return new Cvr(siteConversions * 100.0 / visitors, range[0] * 100, range[1] * 100,
                    String.format("내 사이트의 최근 한 달: 방문자 %,d명 중 문의·구매 %,d건", visitors, siteConversions),
                    GRADE_MEASURED);
        }
        if (visitors != null && visitors > 0 && visitors < MIN_SITE_VISITORS) {
            warnings.add(String.format("사이트 방문자가 %,d명뿐이라 그 전환율은 우연의 영향이 커서 쓰지 않았습니다.", visitors));
        }

        double pct = request.assumedCvrPct() != null && request.assumedCvrPct() > 0
                ? Math.min(request.assumedCvrPct(), 100.0) : DEFAULT_CVR_PCT;
        warnings.add("전환율을 뒷받침할 실측이 없어 가정값으로 계산했습니다. 사이트의 한 달 방문자수와 문의 건수를 입력하면 실측으로 바뀌고, "
                + "광고를 2~4주 운영한 뒤 계획을 다시 받으면 광고 실적으로 바뀝니다.");
        return new Cvr(pct, pct * 0.5, Math.min(pct * 1.5, 100.0),
                request.assumedCvrPct() != null && request.assumedCvrPct() > 0 ? "입력한 가정값" : "기본 가정값",
                GRADE_ASSUMED);
    }

    /** 전환 1건의 매출을 알면, 수익이 남는(또는 목표 수익률이 나오는) 클릭 단가 한도를 구한다. */
    private Long maxCpc(PlanRequest request, Cvr cvr, List<Basis> basis) {
        if (request.saleValue() == null || request.saleValue() <= 0) {
            return null;
        }
        boolean hasTarget = request.targetRoasPct() != null && request.targetRoasPct() > 0;
        double roas = hasTarget ? request.targetRoasPct() : 100.0;
        double allowableCpa = EfficiencyMath.allowableCpa(request.saleValue(), roas);
        long maxCpc = Math.max(1, EfficiencyMath.maxCpc(cvr.pct() / 100.0, allowableCpa));
        basis.add(new Basis("클릭 단가 한도", String.format("%,d원", maxCpc),
                String.format("전환 1건 매출 %,d원 ÷ %s %.0f%% × 전환율 %.2f%%. 추가 클릭이 이보다 비싸지는 입찰가는 계획에 넣지 않음",
                        request.saleValue(), hasTarget ? "목표 광고수익률" : "본전 기준 수익률", roas, cvr.pct()),
                cvr.grade().equals(GRADE_ASSUMED) ? GRADE_ASSUMED : "계산"));
        return maxCpc;
    }

    // --- 키워드와 예측 ---

    /** 연관 키워드에서 후보를 고르고, 입찰가 단계별 예상 성과를 붙인다. */
    private List<Candidate> findCandidates(List<String> seeds, List<String> warnings, List<Basis> basis) {
        List<String> tokens = seeds.stream().map(s -> s.replace(" ", "").toLowerCase(Locale.ROOT)).toList();

        Map<String, RelatedKeyword> unique = new LinkedHashMap<>();
        for (RelatedKeyword keyword : searchAdClient.relatedKeywords(seeds)) {
            if (keyword.totalCount() >= MIN_MONTHLY_SEARCHES) {
                unique.putIfAbsent(keyword.keyword(), keyword);
            }
        }
        List<RelatedKeyword> shortlist = unique.values().stream()
                .sorted(Comparator.comparing((RelatedKeyword k) -> !isRelevant(k.keyword(), tokens))
                        .thenComparing(Comparator.comparingDouble(PlanService::monthlyClicks).reversed()))
                .limit(MAX_CANDIDATES)
                .toList();
        if (shortlist.isEmpty()) {
            return List.of();
        }
        basis.add(new Basis("월 검색량·경쟁 정도", "후보 " + shortlist.size() + "개",
                "네이버 키워드도구의 최근 한 달 집계", GRADE_MEASURED));

        Map<String, Long> bids = searchAdClient.estimateBidForPosition(
                shortlist.stream().map(RelatedKeyword::keyword).toList(), TARGET_POSITION, BID_DEVICE);

        Map<String, Long> baseBids = new LinkedHashMap<>();
        List<PerformanceEstimate.Query> queries = new ArrayList<>();
        int missingBids = 0;
        for (RelatedKeyword keyword : shortlist) {
            Long estimate = bids.get(keyword.keyword());
            if (estimate == null || estimate <= 0) {
                missingBids++;
                continue;
            }
            long base = clampBid(estimate);
            baseBids.put(keyword.keyword(), base);
            TreeSet<Long> ladder = new TreeSet<>();
            for (double step : BID_LADDER) {
                ladder.add(clampBid(Math.round(base * step / 10.0) * 10));
            }
            ladder.forEach(bid -> queries.add(new PerformanceEstimate.Query(keyword.keyword(), bid)));
        }
        if (missingBids > 0) {
            warnings.add("추정 입찰가를 받지 못한 키워드 " + missingBids + "개는 계획에서 뺐습니다.");
        }

        Map<String, List<Point>> estimated = estimate(queries, warnings);

        List<Candidate> candidates = new ArrayList<>();
        int fallbacks = 0;
        for (RelatedKeyword keyword : shortlist) {
            Long base = baseBids.get(keyword.keyword());
            if (base == null) {
                continue;
            }
            List<Point> points = estimated.getOrDefault(keyword.keyword(), List.of());
            Point reference;
            if (points.isEmpty()) {
                // 예측을 못 받은 키워드: 키워드도구의 월평균 클릭수와 입찰가 상한으로 한 점만 둔다.
                long clicks = Math.max(1, Math.round(monthlyClicks(keyword)));
                reference = new Point(base, clicks, clicks * base, 0);
                points = List.of(reference);
                fallbacks++;
            } else {
                reference = points.stream()
                        .min(Comparator.comparingLong(p -> Math.abs(p.bid() - base)))
                        .orElse(points.get(0));
            }
            candidates.add(new Candidate(keyword.keyword(), keyword.totalCount(), keyword.competition(), points,
                    reference, isRelevant(keyword.keyword(), tokens)));
        }

        int estimatedCount = candidates.size() - fallbacks;
        if (estimatedCount > 0) {
            basis.add(new Basis("예상 클릭수·클릭 단가", "키워드 " + estimatedCount + "개 × 입찰가 4단계",
                    "네이버 성과 예측. 그 입찰가로 걸었을 때의 월간 예상 노출·클릭·비용", GRADE_ESTIMATE));
        }
        if (fallbacks > 0) {
            basis.add(new Basis("예상 클릭수·클릭 단가(대체)", "키워드 " + fallbacks + "개",
                    "성과 예측을 받지 못해 키워드도구의 월평균 클릭수 × 추정 입찰가(단가의 상한)로 계산", GRADE_ASSUMED));
        }
        basis.add(new Basis("입찰가 기준", String.format("%s 평균 %d위", BID_DEVICE, TARGET_POSITION),
                "네이버가 추정한 그 순위의 입찰가를 기준으로 0.4~1.3배 구간을 탐색", GRADE_ESTIMATE));
        return candidates;
    }

    /** 성과 예측을 받아 키워드별로 입찰가 오름차순의 점으로 정리한다. 실패하면 빈 값을 돌려 대체 계산으로 넘어가게 한다. */
    private Map<String, List<Point>> estimate(List<PerformanceEstimate.Query> queries, List<String> warnings) {
        Map<String, List<Point>> result = new LinkedHashMap<>();
        List<PerformanceEstimate> estimates;
        try {
            estimates = searchAdClient.estimatePerformance(queries, ESTIMATE_DEVICE);
        } catch (RuntimeException e) {
            log.warn("네이버 성과 예측 실패. 키워드도구 수치로 대체합니다: {}", e.getMessage());
            warnings.add("네이버 성과 예측을 받지 못해 덜 정확한 방식(월평균 클릭수 × 입찰가 상한)으로 계산했습니다. 잠시 뒤 다시 받아 보세요.");
            return result;
        }
        estimates.stream()
                .sorted(Comparator.comparingLong(PerformanceEstimate::bid))
                .forEach(e -> {
                    List<Point> points = result.computeIfAbsent(e.keyword(), k -> new ArrayList<>());
                    Point last = points.isEmpty() ? null : points.get(points.size() - 1);
                    // 입찰가를 올렸는데 클릭이 늘지 않는 점은 살 이유가 없으므로 버린다.
                    if (e.clicks() > 0 && e.cost() > 0 && (last == null || e.clicks() > last.clicks())) {
                        points.add(new Point(e.bid(), e.clicks(), Math.max(e.cost(), last == null ? 0 : last.cost()),
                                e.impressions()));
                    }
                });
        result.values().removeIf(List::isEmpty);
        return result;
    }

    /**
     * 추가 클릭이 가장 싼 입찰가 단계부터 사들인다. 업종과 관련 있는 키워드를 먼저 쓰고, 모자랄 때만 나머지로 넓힌다.
     * 수익이 남는 클릭 단가 한도가 있으면 그보다 비싼 단계는 사지 않는다.
     */
    private Allocation allocate(List<Candidate> candidates, long clicksNeeded, long budgetCap, Long maxCpc) {
        Map<String, Integer> level = new LinkedHashMap<>();
        Map<String, Point> chosen = new LinkedHashMap<>();
        long clicks = 0;
        long cost = 0;
        boolean blocked = false;

        for (boolean relevantOnly : new boolean[] {true, false}) {
            while (clicks < clicksNeeded && cost < budgetCap) {
                Candidate best = null;
                double bestMarginal = Double.MAX_VALUE;
                for (Candidate c : candidates) {
                    if (relevantOnly && !c.relevant()) {
                        continue;
                    }
                    int next = level.getOrDefault(c.keyword(), -1) + 1;
                    if (next >= c.points().size()) {
                        continue;
                    }
                    Point from = next == 0 ? Point.ZERO : c.points().get(next - 1);
                    Point to = c.points().get(next);
                    double marginal = (to.cost() - from.cost()) / (double) (to.clicks() - from.clicks());
                    if (maxCpc != null && marginal > maxCpc) {
                        blocked = true;
                        continue;
                    }
                    if (marginal < bestMarginal) {
                        bestMarginal = marginal;
                        best = c;
                    }
                }
                if (best == null) {
                    break;
                }
                int next = level.getOrDefault(best.keyword(), -1) + 1;
                Point from = next == 0 ? Point.ZERO : best.points().get(next - 1);
                Point to = best.points().get(next);
                // 이 단계를 다 사면 목표나 예산을 넘는 경우, 필요한 만큼만 산 것으로 보고 입찰가도 그만큼만 올린다.
                double fraction = 1.0;
                if (clicksNeeded != Long.MAX_VALUE) {
                    fraction = Math.min(fraction, (clicksNeeded - clicks) / (double) (to.clicks() - from.clicks()));
                }
                if (budgetCap != Long.MAX_VALUE) {
                    fraction = Math.min(fraction, (budgetCap - cost) / (double) (to.cost() - from.cost()));
                }
                Point taken = fraction >= 1.0 ? to : from.towards(to, fraction, next == 0 ? to.bid() : from.bid());
                if (taken.clicks() <= from.clicks()) {
                    break; // 남은 목표·예산으로는 클릭 한 번도 더 살 수 없다
                }
                clicks += taken.clicks() - from.clicks();
                cost += taken.cost() - from.cost();
                chosen.put(best.keyword(), taken);
                level.put(best.keyword(), next);
                if (fraction < 1.0) {
                    return new Allocation(chosen, clicks, cost, blocked);
                }
            }
        }
        return new Allocation(chosen, clicks, cost, blocked);
    }

    /** 월평균 클릭수가 비어 있으면(검색량이 적은 키워드) 검색량의 1%로 잡는다. */
    private static double monthlyClicks(RelatedKeyword keyword) {
        double clicks = keyword.pcClick() + keyword.mobileClick();
        return clicks >= 1 ? clicks : keyword.totalCount() * 0.01;
    }

    private boolean isRelevant(String keyword, List<String> tokens) {
        String normalized = keyword.replace(" ", "").toLowerCase(Locale.ROOT);
        return tokens.stream().anyMatch(t -> normalized.contains(t) || t.contains(normalized));
    }

    private long clampBid(long bid) {
        return Math.min(Math.max(bid, bidPolicy.minBid()), bidPolicy.maxBid());
    }

    // --- 결과 문장 ---

    private List<Scenario> scenarios(long clicks, long cost, Cvr cvr, Long saleValue) {
        List<Scenario> result = new ArrayList<>();
        for (Object[] s : new Object[][] {{"보수적", cvr.lowPct()}, {"기본", cvr.pct()}, {"낙관적", cvr.highPct()}}) {
            double pct = Math.min((double) s[1], 100.0);
            long conversions = Math.round(clicks * pct / 100.0);
            long cpa = conversions == 0 ? 0 : cost / conversions;
            Long revenue = saleValue != null && saleValue > 0 ? conversions * saleValue : null;
            Double roas = revenue != null && cost > 0 ? round2(revenue * 100.0 / cost) : null;
            result.add(new Scenario((String) s[0], round2(pct), conversions, cpa, revenue, roas));
        }
        return result;
    }

    private List<String> steps(long dailyBudget, long targetCpa, List<PlanKeyword> keywords) {
        long selected = keywords.stream().filter(PlanKeyword::selected).count();
        List<String> steps = new ArrayList<>();
        steps.add("사이트에 네이버 전환 추적을 설치하고 문의·견적·구매 완료를 전환으로 등록합니다. 이것이 없으면 아래 단계의 효과를 확인할 수 없고 자동 운영도 입찰가를 판단하지 못합니다.");
        steps.add(String.format("선택된 키워드 %d개를 한 광고그룹에 등록하고, 표의 입찰가로 시작합니다.", selected));
        steps.add(String.format("캠페인 일예산을 %,d원으로 설정합니다. 하루 지출이 이 금액을 넘지 않게 막아 줍니다.", dailyBudget));
        steps.add("자동 운영을 켭니다. 켜 두면 매일 실적을 받아 입찰가와 일예산을 한도 안에서 조정하고, 한 일을 브리핑에 적어 보고합니다. "
                + "직접 하려면 끄고 브리핑의 권장 조치를 따르면 됩니다.");
        steps.add("첫 1~2주는 클릭이 쌓이는 기간입니다. 자동 운영도 키워드별 클릭이 충분히 쌓이기 전에는 입찰가를 건드리지 않습니다.");
        steps.add(targetCpa > 0
                ? String.format("그 뒤로는 전환당 비용 %,d원을 기준으로 키워드별 입찰가가 조정됩니다. 주간 브리핑에서 유입 검색어 중 업종과 무관한 것만 직접 제외 키워드로 등록하세요.", targetCpa)
                : "그 뒤로는 전환이 나는 키워드와 안 나는 키워드를 나눠 입찰가가 조정됩니다. 주간 브리핑에서 유입 검색어 중 업종과 무관한 것만 직접 제외 키워드로 등록하세요.");
        steps.add("4주 뒤 이 화면에서 계획을 다시 받습니다. 그때는 예측과 가정이 내 광고의 실제 클릭 단가와 전환율로 바뀝니다.");
        return steps;
    }

    private PlanProposal withId(PlanProposal p, Long id) {
        return new PlanProposal(id, p.goalType(), p.seedKeywords(), p.monthlyBudget(), p.dailyBudget(),
                p.expectedClicks(), p.expectedConversions(), p.targetCpa(), p.cvrPct(), p.cvrSource(), p.scenarios(),
                p.keywords(), p.assumptions(), p.steps(), p.warnings(), p.createdAt(),
                p.cvrLowPct(), p.cvrHighPct(), p.maxCpc(), p.basis() != null ? p.basis() : List.of());
    }

    private String toJson(PlanProposal proposal) {
        try {
            return objectMapper.writeValueAsString(proposal);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("계획을 저장하지 못했습니다", e);
        }
    }

    private PlanProposal fromJson(String json) {
        try {
            return objectMapper.readValue(json, PlanProposal.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 계획을 읽지 못했습니다", e);
        }
    }

    private static long roundUp(long value, long unit) {
        return (value + unit - 1) / unit * unit;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    private record Cvr(double pct, double lowPct, double highPct, String source, String grade) {}

    /** 한 입찰가에서의 예상 월간 성과. */
    private record Point(long bid, long clicks, long cost, long impressions) {
        static final Point ZERO = new Point(0, 0, 0, 0);

        /**
         * 이 점에서 to 까지의 구간 중 fraction 만큼만 간 점. 두 예측 사이는 직선으로 본다.
         *
         * @param bidFrom 입찰가를 어디서부터 올릴지. 첫 단계는 그 단계의 입찰가를 그대로 쓴다(그보다 낮은 입찰가의 예측이 없으므로)
         */
        Point towards(Point to, double fraction, long bidFrom) {
            long bid = Math.round((bidFrom + (to.bid() - bidFrom) * fraction + 1e-6) / 10.0) * 10;
            return new Point(Math.max(bid, 10),
                    clicks + (long) Math.floor((to.clicks() - clicks) * fraction + 1e-6),
                    cost + (long) Math.floor((to.cost() - cost) * fraction + 1e-6),
                    impressions + (long) Math.floor((to.impressions() - impressions) * fraction + 1e-6));
        }
    }

    /**
     * @param points    입찰가 오름차순. 클릭도 오름차순이다
     * @param reference 목표 순위 입찰가에 가장 가까운 점. 선택되지 않은 키워드를 표에 보여 줄 때 쓴다
     */
    private record Candidate(String keyword, long searches, String competition, List<Point> points, Point reference,
            boolean relevant) {}

    private record Allocation(Map<String, Point> chosen, long clicks, long cost, boolean blockedByMaxCpc) {}
}
