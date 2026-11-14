package com.marketingagent.domain.ads.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.AdCampaign;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.EfficiencyBidService;
import com.marketingagent.domain.ads.EfficiencyBidService.Analysis;
import com.marketingagent.domain.ads.EfficiencyBidService.KeywordEfficiency;
import com.marketingagent.domain.ads.EfficiencyBidService.Targets;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.domain.ads.KeywordConversionHourlyRepository;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.dto.KeywordConversionAggregate;
import com.marketingagent.domain.ads.planner.PlannerDtos.Action;
import com.marketingagent.domain.ads.planner.PlannerDtos.Briefing;
import com.marketingagent.domain.ads.planner.PlannerDtos.BriefingPeriod;
import com.marketingagent.domain.ads.planner.PlannerDtos.Metric;
import com.marketingagent.domain.ads.planner.PlannerDtos.Pacing;
import com.marketingagent.domain.ads.planner.PlannerDtos.Verdict;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일간·주간 브리핑을 만든다. 직원이 대표에게 올리는 보고서 역할이다.
 *
 * <p>숫자와 판단(광고비를 더 쓸지, 유지할지, 줄일지)은 모두 규칙으로 계산한다. 같은 데이터면 항상 같은 결론이 나오고,
 * 왜 그런 결론인지 근거 수치를 함께 적는다. AI 는 설정돼 있을 때 총평 한 문단을 덧붙이는 데만 쓴다.
 *
 * <p>증감 판단은 하루치 변동에 휘둘리지 않도록 최근 14일을 기준으로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    /** 이보다 클릭이 적으면 판단을 보류한다. */
    static final long MIN_CLICKS_FOR_VERDICT = 100;
    private static final int JUDGE_DAYS = 14;
    private static final double GOOD_CPA_RATIO = 0.8;
    private static final double BAD_CPA_RATIO = 1.3;
    private static final double BUDGET_LIMITED_RATIO = 0.85;
    private static final double OVER_PLAN_RATIO = 1.1;
    private static final long MIN_WASTE_AMOUNT = 5_000;
    private static final int MAX_ACTIONS_PER_TYPE = 5;
    private static final int MAX_EFFICIENCY_ROWS = 15;

    private final KeywordStatDailyRepository statRepository;
    private final KeywordConversionHourlyRepository conversionRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdCampaignRepository campaignRepository;
    private final MarketingPlanRepository planRepository;
    private final BriefingRepository briefingRepository;
    private final BidPolicyProperties bidPolicy;
    private final EfficiencyBidService efficiencyBidService;
    private final AutopilotService autopilotService;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;

    @Value("${ai.provider:stub}")
    private String aiProviderName;

    @Value("${ai.openai.model:}")
    private String aiModel;

    @Transactional
    public Briefing generate(BriefingPeriod period) {
        return generate(period, LocalDate.now().minusDays(1));
    }

    /** @param until 브리핑 대상 기간의 마지막 날 */
    @Transactional
    public Briefing generate(BriefingPeriod period, LocalDate until) {
        boolean daily = period == BriefingPeriod.DAILY;
        int days = daily ? 1 : period == BriefingPeriod.WEEKLY ? 7 : 30;
        // 일간은 하루치를 직전 7일의 하루 평균과, 주간·월간은 같은 길이의 직전 기간과 비교한다.
        int baseDays = daily ? 7 : days;
        String periodLabel = daily ? "일간" : period == BriefingPeriod.WEEKLY ? "주간" : "월간";
        String compare = daily ? "직전 7일의 하루 평균" : "직전 " + baseDays + "일";
        LocalDate since = until.minusDays(days - 1L);
        LocalDate baseUntil = since.minusDays(1);
        LocalDate baseSince = baseUntil.minusDays(baseDays - 1L);
        LocalDate judgeSince = until.minusDays(JUDGE_DAYS - 1L);

        List<KeywordAggregate> judgeRows = statRepository.aggregateByKeyword(judgeSince, until);
        if (judgeRows.isEmpty()) {
            throw new BadRequestException("분석할 성과 데이터가 없습니다. 먼저 검색광고 동기화를 실행하세요.");
        }
        Totals current = totals(since, until);
        // 일간은 하루치를 직전 7일의 하루 평균과 비교한다. 전날 하루와 비교하면 요일 차이에 휘둘린다.
        Totals base = daily ? totals(baseSince, baseUntil).perDay(7) : totals(baseSince, baseUntil);
        Totals judge = Totals.of(judgeRows, revenue(judgeSince, until));

        MarketingPlan plan = planRepository.findTopByOrderByIdDesc().orElse(null);
        long dailyBudget = campaignRepository.findAll().stream()
                .map(AdCampaign::getDailyBudget)
                .filter(b -> b != null && b > 0)
                .mapToLong(Long::longValue)
                .sum();

        Target target = resolveTarget(plan, until);
        Pacing pacing = plan == null ? null : pacing(plan, until);
        Decision decision = decide(judge, target, dailyBudget, pacing);
        List<Action> actions = actions(judgeRows, judge, target);

        String ctrComment = ctrComment(current, base, compare);
        String cvrComment = cvrComment(current, base, judge, daily, compare);
        String headline = String.format("[%s] 광고비 %,d원 · 클릭 %,d회 · 전환 %,d건 · 판단: %s",
                periodLabel, current.cost(), current.clk(), current.conv(), label(decision.verdict()));

        // 키워드별 광고비 대비 효율. 데이터가 모자라면 계산하지 않고 이유만 적는다.
        String efficiencyBasis;
        List<KeywordEfficiency> efficiency = List.of();
        String efficiencyComment = null;
        try {
            Analysis analysis = efficiencyBidService.analyze(
                    new Targets(target.cpa(), autopilotService.targets().targetRoasPct(), target.source()), until);
            efficiencyBasis = analysis.targetLabel();
            efficiency = analysis.keywords().stream().limit(MAX_EFFICIENCY_ROWS).toList();
            efficiencyComment = efficiencyComment(analysis);
        } catch (BadRequestException e) {
            efficiencyBasis = e.getMessage();
        }
        AutopilotDtos.RunResult autopilot = until.equals(LocalDate.now().minusDays(1))
                ? autopilotService.todaysRun().orElse(null) : null;

        List<String> narrative = new ArrayList<>();
        narrative.add(summary(current, base, since, until, daily, compare));
        narrative.add(ctrComment);
        narrative.add(cvrComment);
        if (pacing != null) {
            narrative.add(pacing.comment());
        }
        if (efficiencyComment != null) {
            narrative.add(efficiencyComment);
        }
        narrative.add("광고비 판단: " + decision.reason());
        if (autopilot != null) {
            narrative.add("자동 운영이 오늘 한 일: " + autopilot.summary()
                    + (autopilot.notes().isEmpty() ? "" : " " + String.join(" ", autopilot.notes())));
        }

        String generatedBy = "rule-based";
        if (!"stub".equalsIgnoreCase(aiProviderName)) {
            String comment = aiComment(narrative, actions);
            if (comment != null) {
                narrative.add(0, comment);
                generatedBy = aiProviderName + ":" + aiModel;
            }
        }

        Briefing draft = new Briefing(null, period, since, until, headline, decision.verdict(), decision.reason(),
                dailyBudget, decision.suggestedDailyBudget(), metrics(current, base), pacing, ctrComment,
                cvrComment, actions, narrative, generatedBy, LocalDateTime.now(), efficiencyBasis, efficiency,
                autopilot != null ? autopilot.summary() : null,
                autopilot != null ? autopilot.actions() : List.of());

        String json = toJson(draft);
        BriefingRecord record = briefingRepository.findByPeriodAndUntil(period.name(), until).orElse(null);
        if (record == null) {
            record = briefingRepository.save(new BriefingRecord(period.name(), since, until,
                    decision.verdict().name(), headline, json));
        } else {
            record.update(decision.verdict().name(), headline, json);
        }
        log.info("브리핑 생성: {} {} ~ {} / {}", period, since, until, decision.verdict());
        return withId(draft, record.getId());
    }

    @Transactional(readOnly = true)
    public List<Briefing> recent() {
        return briefingRepository.findTop30ByOrderByUntilDescIdDesc().stream()
                .map(record -> withId(fromJson(record.getContentJson()), record.getId()))
                .toList();
    }

    // --- 판단 ---

    /** 목표 전환당 비용. 자동 운영 설정 → 운영 계획 → 서버 설정 순이고, 아무것도 없으면 최근 30일 평균을 기준으로 삼는다. */
    private Target resolveTarget(MarketingPlan plan, LocalDate until) {
        Targets configured = autopilotService.targets();
        if (configured.targetCpa() > 0) {
            return new Target(configured.targetCpa(), configured.source());
        }
        Totals month = totals(until.minusDays(29), until);
        return new Target((long) month.cpa(), "목표가 없어 최근 30일 평균을 기준으로 삼음");
    }

    private Decision decide(Totals judge, Target target, long dailyBudget, Pacing pacing) {
        if (judge.clk() < MIN_CLICKS_FOR_VERDICT) {
            return new Decision(Verdict.NEED_DATA, String.format(
                    "최근 %d일 클릭이 %,d회뿐이라 판단하기 이릅니다. 클릭이 %,d회 이상 쌓일 때까지 지금 설정을 유지하세요.",
                    JUDGE_DAYS, judge.clk(), MIN_CLICKS_FOR_VERDICT), null);
        }
        if (judge.conv() == 0) {
            return new Decision(Verdict.NEED_DATA, String.format(
                    "최근 %d일 동안 클릭 %,d회, 광고비 %,d원을 썼는데 전환이 한 건도 잡히지 않았습니다. "
                            + "먼저 사이트의 전환 추적이 동작하는지 확인하세요. 추적이 정상인데도 0건이라면 광고보다 랜딩 페이지가 문제이므로 증액하면 안 됩니다.",
                    JUDGE_DAYS, judge.clk(), judge.cost()), null);
        }
        if (target.cpa() <= 0) {
            return new Decision(Verdict.HOLD, "비교할 목표 전환당 비용이 없습니다. 운영 계획을 먼저 받아 목표를 정하세요.", null);
        }

        double cpa = judge.cpa();
        double avgDailySpend = judge.cost() / (double) JUDGE_DAYS;
        String basis = String.format("최근 %d일 전환당 비용 %,d원, 목표 %,d원(%s)",
                JUDGE_DAYS, (long) cpa, target.cpa(), target.source());

        if (cpa <= target.cpa() * GOOD_CPA_RATIO) {
            // 효율이 좋아도 이미 계획한 예산을 넘겨 쓰고 있으면 더 쓰라고 권하지 않는다. 계획을 올릴지는 사람이 정한다.
            if (pacing != null && pacing.monthlyBudget() > 0
                    && pacing.projectedSpend() > pacing.monthlyBudget() * OVER_PLAN_RATIO) {
                return new Decision(Verdict.HOLD, String.format(
                        "%s. 효율은 목표보다 좋습니다. 다만 이 속도면 이번 달 광고비가 약 %,d원으로 계획한 %,d원을 넘습니다. "
                                + "더 늘리기 전에 계획 예산을 올릴지 먼저 정하세요. 올리기로 했다면 운영 계획을 새 예산으로 다시 받으면 됩니다.",
                        basis, pacing.projectedSpend(), pacing.monthlyBudget()), null);
            }
            // 지출이 일예산 합계를 넘으면 일예산이 없는 캠페인이 섞여 있다는 뜻이라 예산 한도로 판단할 수 없다.
            boolean budgetKnown = dailyBudget > 0 && avgDailySpend <= dailyBudget * 1.1;
            if (budgetKnown && avgDailySpend >= dailyBudget * BUDGET_LIMITED_RATIO) {
                long suggested = roundUp((long) (dailyBudget * 1.2), 1_000);
                return new Decision(Verdict.INCREASE, String.format(
                        "%s. 목표보다 20%% 이상 싸게 전환을 얻고 있고, 하루 평균 %,d원을 써서 일예산 %,d원을 거의 다 씁니다. "
                                + "예산 때문에 놓치는 클릭이 있으니 일예산을 %,d원으로 20%% 올려 보세요. 한 번에 더 올리지 말고 1주 뒤 다시 판단합니다.",
                        basis, (long) avgDailySpend, dailyBudget, suggested), suggested);
            }
            String why;
            if (dailyBudget <= 0) {
                why = "일예산 제한이 걸려 있지 않아";
            } else if (!budgetKnown) {
                why = String.format("하루 평균 지출(%,d원)이 설정된 일예산 합계(%,d원)보다 커서 일예산이 없는 캠페인이 있는 것으로 보여",
                        (long) avgDailySpend, dailyBudget);
            } else {
                why = String.format("하루 평균 %,d원으로 일예산 %,d원을 다 쓰지 못하고 있어", (long) avgDailySpend, dailyBudget);
            }
            return new Decision(Verdict.INCREASE, String.format(
                    "%s. 효율이 좋아 더 써도 됩니다. 다만 %s 일예산을 올리는 것으로는 지출이 늘지 않습니다. "
                            + "효율 좋은 키워드의 입찰가를 올리거나 키워드를 추가해 노출을 넓히는 쪽이 맞습니다.",
                    basis, why), null);
        }
        if (cpa >= target.cpa() * BAD_CPA_RATIO) {
            long lowered = Math.max(1_000, (long) (dailyBudget * 0.8) / 1_000 * 1_000);
            // 이미 최저 수준이면 더 줄이라고 권할 수 없다.
            Long suggested = dailyBudget > 0 && lowered < dailyBudget ? lowered : null;
            String next = suggested != null ? String.format("일예산을 %,d원으로 20%% 줄이세요.", suggested)
                    : dailyBudget > 0 ? "일예산은 이미 최저 수준이라 더 줄일 수 없으니, 입찰가를 낮추거나 효율이 나쁜 키워드를 끄는 수밖에 없습니다."
                    : "캠페인에 일예산을 걸어 지출을 제한하세요.";
            return new Decision(Verdict.DECREASE, String.format(
                    "%s. 목표보다 30%% 이상 비쌉니다. 예산을 일괄로 깎기 전에 아래 조치의 전환 없는 키워드부터 정리하세요. "
                            + "정리 후에도 1주 동안 나아지지 않으면 %s",
                    basis, next), suggested);
        }
        return new Decision(Verdict.HOLD, basis + ". 목표 범위 안이므로 지금 수준을 유지하세요.", null);
    }

    private List<Action> actions(List<KeywordAggregate> rows, Totals judge, Target target) {
        Map<String, String> names = keywordRepository.findAll().stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, AdKeyword::getKeyword, (a, b) -> a));
        List<Action> actions = new ArrayList<>();

        if (judge.conv() == 0) {
            actions.add(new Action("전환 추적 확인", "사이트",
                    "문의·견적·구매 완료가 네이버 전환으로 잡히는지 확인하세요. 이것 없이는 어떤 키워드가 돈을 버는지 알 수 없습니다."));
        }

        long wasteLine = Math.max(target.cpa(), MIN_WASTE_AMOUNT);
        rows.stream()
                .filter(r -> nz(r.ccnt()) == 0 && nz(r.salesAmt()) >= wasteLine && judge.conv() > 0)
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.salesAmt())).reversed())
                .limit(MAX_ACTIONS_PER_TYPE)
                .forEach(r -> actions.add(new Action("입찰 낮추기 또는 중지", name(names, r), String.format(
                        "%d일간 광고비 %,d원, 클릭 %,d회, 전환 0건", JUDGE_DAYS, nz(r.salesAmt()), nz(r.clkCnt())))));

        if (target.cpa() > 0) {
            rows.stream()
                    .filter(r -> nz(r.ccnt()) > 0 && nz(r.impCnt()) > 0
                            && nz(r.salesAmt()) / (double) nz(r.ccnt()) <= target.cpa() * 0.7
                            && avgRank(r) > 3.0)
                    .sorted(Comparator.comparingDouble(r -> nz(r.salesAmt()) / (double) nz(r.ccnt())))
                    .limit(MAX_ACTIONS_PER_TYPE)
                    .forEach(r -> actions.add(new Action("입찰 올리기", name(names, r), String.format(
                            "전환당 %,d원으로 싸게 전환되는데 평균 %.1f위에 노출됩니다. 순위를 올리면 전환이 더 나올 여지가 있습니다.",
                            nz(r.salesAmt()) / nz(r.ccnt()), avgRank(r)))));
        }

        rows.stream()
                .filter(r -> nz(r.impCnt()) >= 500 && nz(r.clkCnt()) * 100.0 / nz(r.impCnt()) < 0.5)
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> nz(r.impCnt())).reversed())
                .limit(3)
                .forEach(r -> actions.add(new Action("광고 문구 점검", name(names, r), String.format(
                        "노출 %,d회 중 클릭 %,d회(클릭률 %.2f%%). 검색한 사람이 원하는 것과 광고 문구가 맞는지 보세요.",
                        nz(r.impCnt()), nz(r.clkCnt()), nz(r.clkCnt()) * 100.0 / nz(r.impCnt())))));
        return actions;
    }

    private Pacing pacing(MarketingPlan plan, LocalDate until) {
        Totals month = totals(until.withDayOfMonth(1), until);
        int elapsed = until.getDayOfMonth();
        int daysInMonth = until.lengthOfMonth();
        long projectedSpend = month.cost() * daysInMonth / elapsed;
        long projectedConversions = Math.round(month.conv() * (double) daysInMonth / elapsed);

        StringBuilder comment = new StringBuilder(String.format(
                "이번 달 %d일까지 광고비 %,d원, 전환 %,d건입니다. 이 속도면 월말에 광고비 약 %,d원(계획 %,d원), 전환 약 %,d건(목표 %,d건)이 됩니다.",
                elapsed, month.cost(), month.conv(), projectedSpend, plan.getMonthlyBudget(),
                projectedConversions, plan.getTargetConversions()));
        if (plan.getMonthlyBudget() > 0 && projectedSpend > plan.getMonthlyBudget() * 1.1) {
            comment.append(" 계획한 예산을 넘길 것으로 보입니다.");
        } else if (plan.getMonthlyBudget() > 0 && projectedSpend < plan.getMonthlyBudget() * 0.7) {
            comment.append(" 계획한 예산을 다 쓰지 못하고 있습니다. 노출이 부족하다는 뜻이므로 입찰가나 키워드 수를 점검하세요.");
        }
        if (plan.getTargetConversions() > 0 && elapsed >= 7 && projectedConversions < plan.getTargetConversions() * 0.8) {
            comment.append(" 전환이 목표에 못 미칠 것으로 보입니다.");
        }
        return new Pacing(plan.getMonthlyBudget(), month.cost(), projectedSpend, plan.getTargetConversions(),
                month.conv(), projectedConversions, comment.toString());
    }

    // --- 문장 ---

    /** 키워드 효율 계산을 한 문단으로 줄인다. */
    private String efficiencyComment(Analysis analysis) {
        long up = analysis.keywords().stream().filter(k -> "올리기".equals(k.verdict())).count();
        long down = analysis.keywords().stream().filter(k -> "내리기".equals(k.verdict())).count();
        long stop = analysis.keywords().stream().filter(k -> "중지 검토".equals(k.verdict())).count();
        long wasted = analysis.keywords().stream().filter(k -> "중지 검토".equals(k.verdict()))
                .mapToLong(KeywordEfficiency::cost).sum();
        StringBuilder text = new StringBuilder(String.format(
                "키워드 효율(최근 %d일, %s): 키워드마다 클릭 한 번에 쓸 수 있는 돈과 실제 클릭 단가를 비교했습니다. "
                        + "입찰가를 올려도 되는 키워드 %d개, 내려야 하는 키워드 %d개",
                EfficiencyBidService.WINDOW_DAYS, analysis.targetLabel(), up, down));
        if (stop > 0) {
            text.append(String.format(", 전환 없이 %,d원을 쓴 키워드 %d개", wasted, stop));
        }
        long watching = analysis.keywords().stream().filter(k -> "지켜보는 중".equals(k.verdict())).count();
        if (watching > 0) {
            text.append(String.format(", 최근 입찰가를 바꿔 결과를 지켜보는 키워드 %d개", watching));
        }
        text.append("입니다. 아래 표에 키워드별 계산이 있습니다.");
        return text.toString();
    }

    private String summary(Totals current, Totals base, LocalDate since, LocalDate until, boolean daily,
            String compare) {
        String range = daily ? until.toString() : since + " ~ " + until;
        return String.format("%s 동안 광고비 %,d원으로 노출 %,d회, 클릭 %,d회, 전환 %,d건을 얻었습니다. %s과 비교하면 광고비 %s, 클릭 %s, 전환 %s입니다.",
                range, current.cost(), current.imp(), current.clk(), current.conv(), compare,
                change(current.cost(), base.cost()), change(current.clk(), base.clk()),
                change(current.conv(), base.conv()));
    }

    private String ctrComment(Totals current, Totals base, String compare) {
        if (current.imp() == 0) {
            return "클릭률: 이 기간에 광고가 노출되지 않았습니다. 캠페인이 꺼져 있거나 예산·입찰가가 너무 낮지 않은지 확인하세요.";
        }
        double ctr = current.ctr();
        String level = ctr < 1.0 ? "낮은 편입니다. 광고 문구와 키워드가 검색 의도와 맞는지 점검하세요"
                : ctr < 3.0 ? "보통 수준입니다" : "좋은 편입니다";
        return String.format("클릭률: %.2f%%로 %s (이 도구의 기준: 1%% 미만 낮음, 3%% 이상 좋음). %s 대비 %s.",
                ctr, level, compare, change(ctr, base.ctr()));
    }

    private String cvrComment(Totals current, Totals base, Totals judge, boolean daily, String compare) {
        if (judge.conv() == 0) {
            return "전환율: 최근 14일 동안 전환이 잡히지 않아 평가할 수 없습니다.";
        }
        if (current.clk() == 0) {
            return "전환율: 이 기간에 클릭이 없어 계산할 수 없습니다.";
        }
        String note = daily && current.clk() < 30 ? " 하루 클릭이 적어 변동이 크니 주간 수치를 함께 보세요." : "";
        return String.format("전환율: %.2f%%(클릭 %,d회 중 %,d건), 전환당 비용 %s. %s 대비 전환율 %s.%s",
                current.cvr(), current.clk(), current.conv(),
                current.conv() == 0 ? "계산 불가" : String.format("%,d원", (long) current.cpa()),
                compare, change(current.cvr(), base.cvr()), note);
    }

    private String aiComment(List<String> narrative, List<Action> actions) {
        String system = """
                당신은 소기업 대표에게 광고 운영 결과를 보고하는 마케팅 담당자입니다.
                주어진 내용만 근거로 2~3문장의 총평을 한국어로 쓰십시오. 주어지지 않은 수치나 사실은 쓰지 마십시오.
                광고비 판단 결론은 주어진 것과 같아야 합니다.
                """;
        String user = String.join("\n", narrative) + "\n\n권장 조치:\n" + actions.stream()
                .map(a -> "- " + a.type() + ": " + a.target() + " (" + a.detail() + ")")
                .collect(Collectors.joining("\n"));
        try {
            return "총평: " + aiProvider.generate(new AiRequest(system, user, null)).content().trim();
        } catch (Exception e) {
            log.warn("브리핑 AI 총평 생성 실패. 규칙 기반 문장만 씁니다: {}", e.getMessage());
            return null;
        }
    }

    private List<Metric> metrics(Totals current, Totals base) {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(metric("광고비", current.cost(), base.cost(), "원"));
        metrics.add(metric("노출수", current.imp(), base.imp(), "회"));
        metrics.add(metric("클릭수", current.clk(), base.clk(), "회"));
        metrics.add(metric("클릭률", current.ctr(), base.ctr(), "%"));
        metrics.add(metric("클릭당 비용", current.cpc(), base.cpc(), "원"));
        metrics.add(metric("전환수", current.conv(), base.conv(), "건"));
        metrics.add(metric("전환율", current.cvr(), base.cvr(), "%"));
        metrics.add(metric("전환당 비용", current.cpa(), base.cpa(), "원"));
        if (current.revenue() > 0 || base.revenue() > 0) {
            metrics.add(metric("전환매출", current.revenue(), base.revenue(), "원"));
            metrics.add(metric("광고수익률", current.roas(), base.roas(), "%"));
        }
        return metrics;
    }

    private Metric metric(String label, double current, double previous, String unit) {
        Double changePct = previous == 0 ? null : round2((current - previous) / previous * 100.0);
        return new Metric(label, round2(current), round2(previous), unit, changePct);
    }

    private String change(double current, double previous) {
        if (previous == 0) {
            return current == 0 ? "변동 없음" : "비교 기준 없음";
        }
        return String.format("%+.1f%%", (current - previous) / previous * 100.0);
    }

    private String label(Verdict verdict) {
        return switch (verdict) {
            case INCREASE -> "더 써도 됨";
            case HOLD -> "유지";
            case DECREASE -> "줄이기";
            case NEED_DATA -> "판단 보류";
        };
    }

    // --- 집계 ---

    private Totals totals(LocalDate since, LocalDate until) {
        return Totals.of(statRepository.aggregateByKeyword(since, until), revenue(since, until));
    }

    private long revenue(LocalDate since, LocalDate until) {
        return conversionRepository.aggregateByKeyword(since, until).stream()
                .map(KeywordConversionAggregate::convAmt)
                .mapToLong(BriefingService::nz)
                .sum();
    }

    private String name(Map<String, String> names, KeywordAggregate row) {
        return names.getOrDefault(row.nccKeywordId(), row.nccKeywordId());
    }

    private double avgRank(KeywordAggregate row) {
        return row.rnkWeighted() == null || nz(row.impCnt()) == 0 ? 0.0 : row.rnkWeighted() / nz(row.impCnt());
    }

    private Briefing withId(Briefing b, Long id) {
        return new Briefing(id, b.period(), b.since(), b.until(), b.headline(), b.verdict(), b.verdictReason(),
                b.currentDailyBudget(), b.suggestedDailyBudget(), b.metrics(), b.pacing(), b.ctrComment(),
                b.cvrComment(), b.actions(), b.narrative(), b.generatedBy(), b.createdAt(), b.efficiencyBasis(),
                b.efficiency() != null ? b.efficiency() : List.of(), b.autopilotSummary(),
                b.autopilotActions() != null ? b.autopilotActions() : List.of());
    }

    private String toJson(Briefing briefing) {
        try {
            return objectMapper.writeValueAsString(briefing);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브리핑을 저장하지 못했습니다", e);
        }
    }

    private Briefing fromJson(String json) {
        try {
            return objectMapper.readValue(json, Briefing.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 브리핑을 읽지 못했습니다", e);
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

    private record Target(long cpa, String source) {}

    private record Decision(Verdict verdict, String reason, Long suggestedDailyBudget) {}

    /** 한 기간의 합계. 비율은 합계에서 계산한다. */
    record Totals(long imp, long clk, long cost, long conv, long revenue) {

        static Totals of(List<KeywordAggregate> rows, long revenue) {
            long imp = 0;
            long clk = 0;
            long cost = 0;
            long conv = 0;
            for (KeywordAggregate row : rows) {
                imp += nz(row.impCnt());
                clk += nz(row.clkCnt());
                cost += nz(row.salesAmt());
                conv += nz(row.ccnt());
            }
            return new Totals(imp, clk, cost, conv, revenue);
        }

        Totals perDay(int days) {
            return new Totals(imp / days, clk / days, cost / days, conv / days, revenue / days);
        }

        double ctr() {
            return imp == 0 ? 0.0 : clk * 100.0 / imp;
        }

        double cpc() {
            return clk == 0 ? 0.0 : cost / (double) clk;
        }

        double cvr() {
            return clk == 0 ? 0.0 : conv * 100.0 / clk;
        }

        double cpa() {
            return conv == 0 ? 0.0 : cost / (double) conv;
        }

        double roas() {
            return cost == 0 ? 0.0 : revenue * 100.0 / cost;
        }
    }
}
