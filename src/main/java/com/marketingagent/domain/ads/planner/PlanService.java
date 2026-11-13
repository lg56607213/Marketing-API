package com.marketingagent.domain.ads.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.planner.PlannerDtos.GoalType;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanKeyword;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanRequest;
import com.marketingagent.domain.ads.planner.PlannerDtos.Scenario;
import com.marketingagent.integration.naver.SearchAdClient;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표(월 전환수 또는 월 예산)를 받아 키워드·예산·기대 성과가 담긴 운영 계획을 제안한다.
 *
 * <p>계산 방식은 일부러 단순하고 보수적이다. 네이버가 알려주는 월평균 클릭수와 목표 순위의 추정 입찰가를 쓰고,
 * 입찰가를 클릭 단가의 상한으로 본다(실제 단가는 보통 이보다 낮다). 전환율은 실적이 충분하면 실적을,
 * 아니면 가정값을 쓰며 어느 쪽인지 제안에 적는다. 이 서비스는 네이버 계정에 아무것도 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    static final double DEFAULT_CVR_PCT = 2.0;
    private static final int TARGET_POSITION = 3;
    private static final String DEVICE = "MOBILE";
    private static final int MAX_CANDIDATES = 20;
    private static final long MIN_MONTHLY_SEARCHES = 30;
    private static final long MIN_CLICKS_FOR_ACTUAL_CVR = 100;
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

        Cvr cvr = resolveCvr(request.assumedCvrPct(), warnings);
        List<Candidate> candidates = findCandidates(seeds, warnings);
        if (candidates.isEmpty()) {
            throw new BadRequestException("입력한 키워드로는 검색량이 있는 후보를 찾지 못했습니다. 더 일반적인 키워드로 다시 시도하세요.");
        }

        long budgetCap = byConversions ? Long.MAX_VALUE : request.monthlyBudget();
        long clicksNeeded = byConversions
                ? (long) Math.ceil(request.targetConversions() / (cvr.pct() / 100.0))
                : Long.MAX_VALUE;

        long clicks = 0;
        long cost = 0;
        List<PlanKeyword> keywords = new ArrayList<>();
        for (Candidate c : candidates) {
            long takeClicks = 0;
            if (clicks < clicksNeeded && cost < budgetCap) {
                takeClicks = Math.min(c.clicks(), clicksNeeded - clicks);
                takeClicks = Math.min(takeClicks, (budgetCap - cost) / c.bid());
            }
            clicks += takeClicks;
            cost += takeClicks * c.bid();
            keywords.add(new PlanKeyword(c.keyword(), c.searches(), c.competition(), c.bid(),
                    takeClicks, takeClicks * c.bid(), takeClicks > 0));
        }

        long expectedConversions = Math.round(clicks * cvr.pct() / 100.0);
        if (byConversions && clicks < clicksNeeded) {
            warnings.add(String.format(
                    "후보 키워드의 검색량으로는 목표 %,d건에 못 미칩니다. 이 키워드들로 기대할 수 있는 전환은 월 %,d건 안팎입니다. "
                            + "핵심 키워드를 넓히거나 목표를 낮추세요.",
                    request.targetConversions(), expectedConversions));
        }
        if (clicks == 0) {
            throw new BadRequestException("이 예산으로는 가장 싼 키워드의 클릭 1회도 살 수 없습니다. 예산을 늘려 주세요.");
        }

        long monthlyBudget = byConversions ? roundUp(cost, 10_000) : request.monthlyBudget();
        long dailyBudget = roundUp((long) Math.ceil(monthlyBudget / 30.0), 1_000);
        long targetCpa = expectedConversions == 0 ? 0 : cost / expectedConversions;
        long targetConversions = byConversions ? request.targetConversions() : expectedConversions;

        assumptions.add(String.format("전환율 %.2f%% (%s)", cvr.pct(), cvr.source()));
        assumptions.add(String.format("%s 기준 평균 %d위 노출을 목표로 한 네이버 추정 입찰가를 썼습니다.", DEVICE, TARGET_POSITION));
        assumptions.add("추정 입찰가를 클릭 단가의 상한으로 보고 계산했습니다. 실제 클릭 단가는 보통 이보다 낮아 예산이 남을 수 있습니다.");
        assumptions.add("기대 클릭수는 네이버 키워드도구의 월평균 클릭수입니다. 계절과 경쟁 상황에 따라 달라집니다.");

        PlanProposal draft = new PlanProposal(null, request.goalType(), seeds, monthlyBudget, dailyBudget, clicks,
                expectedConversions, targetCpa, round2(cvr.pct()), cvr.source(),
                scenarios(clicks, cost, cvr.pct(), request.saleValue()), keywords, assumptions,
                steps(dailyBudget, targetCpa, keywords), warnings, LocalDateTime.now());

        MarketingPlan saved = planRepository.save(new MarketingPlan(request.goalType().name(), monthlyBudget,
                targetConversions, targetCpa, toJson(draft)));
        log.info("운영 계획 제안: 목표 {} / 월 {}원 / 기대 전환 {}건 / 키워드 {}개",
                request.goalType(), monthlyBudget, expectedConversions,
                keywords.stream().filter(PlanKeyword::selected).count());
        return withId(draft, saved.getId());
    }

    @Transactional(readOnly = true)
    public Optional<PlanProposal> current() {
        return planRepository.findTopByOrderByIdDesc().map(plan -> withId(fromJson(plan.getProposalJson()), plan.getId()));
    }

    /** 최근 30일 실적이 충분하면 실제 전환율을, 아니면 가정값을 쓴다. */
    private Cvr resolveCvr(Double assumedPct, List<String> warnings) {
        LocalDate until = LocalDate.now().minusDays(1);
        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(until.minusDays(29), until);
        long clicks = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long conversions = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();

        if (clicks >= MIN_CLICKS_FOR_ACTUAL_CVR && conversions > 0) {
            return new Cvr(conversions * 100.0 / clicks,
                    String.format("최근 30일 실적: 클릭 %,d회 중 전환 %,d건", clicks, conversions));
        }
        double pct = assumedPct != null && assumedPct > 0 ? Math.min(assumedPct, 100.0) : DEFAULT_CVR_PCT;
        if (clicks >= MIN_CLICKS_FOR_ACTUAL_CVR) {
            warnings.add(String.format(
                    "최근 30일에 클릭이 %,d회 있었지만 전환이 한 건도 잡히지 않았습니다. 사이트에 전환 추적이 설치돼 있는지 먼저 확인하세요. "
                            + "추적이 없으면 어떤 계획도 검증할 수 없습니다.", clicks));
        } else {
            warnings.add("아직 실적이 부족해 전환율을 가정값으로 계산했습니다. 2~4주 운영한 뒤 계획을 다시 받으면 실제 전환율로 바뀝니다.");
        }
        return new Cvr(pct, "가정값. 실적이 쌓이면 실제 값으로 대체");
    }

    /** 연관 키워드에서 후보를 고르고 추정 입찰가를 붙인다. 핵심 키워드와 관련 있는 것, 그다음 싼 것 순이다. */
    private List<Candidate> findCandidates(List<String> seeds, List<String> warnings) {
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

        Map<String, Long> bids = searchAdClient.estimateBidForPosition(
                shortlist.stream().map(RelatedKeyword::keyword).toList(), TARGET_POSITION, DEVICE);

        List<Candidate> candidates = new ArrayList<>();
        int missingBids = 0;
        for (RelatedKeyword keyword : shortlist) {
            Long estimate = bids.get(keyword.keyword());
            if (estimate == null || estimate <= 0) {
                missingBids++;
                continue;
            }
            long bid = Math.min(Math.max(estimate, bidPolicy.minBid()), bidPolicy.maxBid());
            long clicks = Math.max(1, Math.round(monthlyClicks(keyword)));
            candidates.add(new Candidate(keyword.keyword(), keyword.totalCount(), keyword.competition(), bid,
                    clicks, isRelevant(keyword.keyword(), tokens)));
        }
        if (missingBids > 0) {
            warnings.add("추정 입찰가를 받지 못한 키워드 " + missingBids + "개는 계획에서 뺐습니다.");
        }
        candidates.sort(Comparator.comparing((Candidate c) -> !c.relevant()).thenComparingLong(Candidate::bid));
        return candidates;
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

    private List<Scenario> scenarios(long clicks, long cost, double cvrPct, Long saleValue) {
        List<Scenario> result = new ArrayList<>();
        for (Object[] s : new Object[][] {{"보수적", 0.5}, {"기본", 1.0}, {"낙관적", 1.5}}) {
            double pct = Math.min(cvrPct * (double) s[1], 100.0);
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
        steps.add("사이트에 네이버 전환 추적을 설치하고 문의·견적·구매 완료를 전환으로 등록합니다. 이것이 없으면 아래 단계의 효과를 확인할 수 없습니다.");
        steps.add(String.format("선택된 키워드 %d개를 한 광고그룹에 등록하고, 표의 추천 입찰가로 시작합니다.", selected));
        steps.add(String.format("캠페인 일예산을 %,d원으로 설정합니다. 하루 지출이 이 금액을 넘지 않게 막아 줍니다.", dailyBudget));
        steps.add("첫 1주는 입찰가를 바꾸지 말고 데이터를 모읍니다. 그동안 일간 브리핑에서 이상 지출만 확인합니다.");
        steps.add("1주 뒤 주간 브리핑을 보고, 유입 검색어 중 업종과 무관한 것을 제외 키워드로 등록합니다.");
        steps.add(targetCpa > 0
                ? String.format("2주 뒤부터 전환당 비용이 %,d원을 크게 넘는 키워드는 입찰가를 낮추고, 훨씬 낮은 키워드는 올립니다. 브리핑이 대상을 알려줍니다.", targetCpa)
                : "2주 뒤부터 전환이 나는 키워드와 안 나는 키워드를 나눠 입찰가를 조정합니다. 브리핑이 대상을 알려줍니다.");
        steps.add("4주 뒤 이 화면에서 계획을 다시 받습니다. 그때는 가정이 아닌 실제 전환율로 예산이 계산됩니다.");
        return steps;
    }

    private PlanProposal withId(PlanProposal p, Long id) {
        return new PlanProposal(id, p.goalType(), p.seedKeywords(), p.monthlyBudget(), p.dailyBudget(),
                p.expectedClicks(), p.expectedConversions(), p.targetCpa(), p.cvrPct(), p.cvrSource(), p.scenarios(),
                p.keywords(), p.assumptions(), p.steps(), p.warnings(), p.createdAt());
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

    private record Cvr(double pct, String source) {}

    private record Candidate(String keyword, long searches, String competition, long bid, long clicks,
            boolean relevant) {}
}
