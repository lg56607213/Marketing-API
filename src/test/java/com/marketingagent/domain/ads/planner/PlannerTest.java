package com.marketingagent.domain.ads.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.security.OwnerContext;
import com.marketingagent.domain.ads.AdSyncService;
import com.marketingagent.domain.ads.planner.PlannerDtos.Briefing;
import com.marketingagent.domain.ads.planner.PlannerDtos.BriefingPeriod;
import com.marketingagent.domain.ads.planner.PlannerDtos.GoalType;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanKeyword;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanRequest;
import com.marketingagent.domain.ads.planner.PlannerDtos.Verdict;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Stub 데이터로 계획 제안과 브리핑 판단 규칙을 확인한다. 회원마다 다른 소유자 ID 를 써서 서로 섞이지 않게 한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:planner;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
class PlannerTest {

    private static final List<String> SEEDS = List.of("화물차", "탑차");

    @Autowired
    private PlanService planService;
    @Autowired
    private BriefingService briefingService;
    @Autowired
    private AdSyncService adSyncService;
    @Autowired
    private MarketingPlanRepository planRepository;
    @Autowired
    private com.marketingagent.domain.ads.KeywordStatDailyRepository statRepository;
    @Autowired
    private com.marketingagent.domain.ads.AdCampaignRepository campaignRepository;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private org.springframework.transaction.support.TransactionTemplate transaction;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @Test
    void proposesBudgetFromConversionGoalUsingAssumedRateWhenThereIsNoHistory() {
        OwnerContext.runAs(8101L, () -> {
            PlanProposal plan = planService.propose(
                    new PlanRequest(SEEDS, GoalType.CONVERSIONS, 10L, null, 500_000L, null, null, null, null));

            assertThat(plan.id()).isNotNull();
            assertThat(plan.cvrPct()).isEqualTo(PlanService.DEFAULT_CVR_PCT);
            assertThat(plan.cvrSource()).contains("가정값");
            assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("가정값"));

            // 목표 10건을 전환율 2%로 얻으려면 클릭 500회가 필요하다. 필요한 만큼만 산다.
            assertThat(plan.expectedClicks()).isEqualTo(500);
            assertThat(plan.expectedConversions()).isEqualTo(10);
            assertThat(plan.cvrLowPct()).isEqualTo(1.0);
            assertThat(plan.cvrHighPct()).isEqualTo(3.0);
            // 무엇이 실측이고 무엇이 가정인지 밝힌다.
            assertThat(plan.basis()).anySatisfy(b -> {
                assertThat(b.item()).isEqualTo("전환율");
                assertThat(b.grade()).isEqualTo("가정");
            });
            assertThat(plan.basis()).anySatisfy(b -> assertThat(b.grade()).isEqualTo("네이버 예측"));
            List<PlanKeyword> selected = plan.keywords().stream().filter(PlanKeyword::selected).toList();
            assertThat(selected).isNotEmpty();
            long cost = selected.stream().mapToLong(PlanKeyword::expectedCost).sum();
            assertThat(selected.stream().mapToLong(PlanKeyword::expectedClicks).sum()).isEqualTo(plan.expectedClicks());
            assertThat(selected).allSatisfy(k -> {
                assertThat(k.expectedCpc()).isPositive().isLessThanOrEqualTo(k.suggestedBid());
                assertThat(k.expectedImpressions()).isGreaterThan(k.expectedClicks());
            });
            assertThat(plan.monthlyBudget()).isGreaterThanOrEqualTo(cost).isLessThan(cost + 10_000);
            assertThat(plan.monthlyBudget() % 10_000).isZero();
            assertThat(plan.dailyBudget() * 30).isGreaterThanOrEqualTo(plan.monthlyBudget());
            assertThat(plan.targetCpa()).isEqualTo(cost / plan.expectedConversions());

            assertThat(plan.scenarios()).extracting(PlannerDtos.Scenario::name)
                    .containsExactly("보수적", "기본", "낙관적");
            assertThat(plan.scenarios().get(0).conversions()).isEqualTo(Math.round(plan.expectedClicks() * 0.01));
            assertThat(plan.scenarios().get(1).revenue()).isEqualTo(plan.expectedConversions() * 500_000L);
            // 전환 1건 매출 500,000원 × 전환율 2% = 클릭당 10,000원까지가 본전이다.
            assertThat(plan.maxCpc()).isEqualTo(10_000);
            assertThat(plan.scenarios().get(1).roasPct()).isPositive();
            assertThat(plan.steps()).hasSizeGreaterThanOrEqualTo(5);
            assertThat(plan.assumptions()).isNotEmpty();

            // 후보 키워드의 검색량보다 큰 목표를 주면 부족하다고 알려준다.
            PlanProposal tooBig = planService.propose(
                    new PlanRequest(SEEDS, GoalType.CONVERSIONS, 10_000L, null, null, null, null, null, null));
            assertThat(tooBig.warnings()).anySatisfy(w -> assertThat(w).contains("못 미칩니다"));
            assertThat(tooBig.expectedConversions()).isLessThan(10_000);
            planService.propose(new PlanRequest(SEEDS, GoalType.CONVERSIONS, 10L, null, 500_000L, null, null, null, null));
            plan = planService.current().orElseThrow();

            assertThat(planService.current()).get().extracting(PlanProposal::id).isEqualTo(plan.id());
        });
        // 다른 회원에게는 이 계획이 보이지 않는다.
        assertThat((boolean) OwnerContext.<Boolean>callAs(8102L, () -> planService.current().isPresent())).isFalse();
    }

    @Test
    void staysWithinAGivenBudgetAndUsesActualRateOnceThereIsHistory() {
        OwnerContext.runAs(8103L, () -> {
            adSyncService.sync(30);
            PlanProposal plan = planService.propose(
                    new PlanRequest(SEEDS, GoalType.BUDGET, null, 300_000L, null, null, null, null, null));

            assertThat(plan.monthlyBudget()).isEqualTo(300_000);
            assertThat(plan.cvrSource()).contains("최근 30일 실적");
            assertThat(plan.cvrPct()).isGreaterThan(PlanService.DEFAULT_CVR_PCT);
            long cost = plan.keywords().stream().filter(PlanKeyword::selected)
                    .mapToLong(PlanKeyword::expectedCost).sum();
            assertThat(cost).isPositive().isLessThanOrEqualTo(300_000);
            assertThat(plan.expectedConversions()).isPositive();
            assertThat(plan.scenarios().get(1).revenue()).isNull();
        });
    }

    @Test
    void usesMeasuredSiteRateInsteadOfAnAssumptionAndStopsBuyingUnprofitableClicks() {
        OwnerContext.runAs(8109L, () -> {
            // 사이트 방문자 2,000명 중 문의 60건 = 3%. 가정값 2% 대신 이 실측을 쓴다.
            PlanProposal measured = planService.propose(
                    new PlanRequest(SEEDS, GoalType.CONVERSIONS, 10L, null, null, null, 2_000L, 60L, null));
            assertThat(measured.cvrPct()).isEqualTo(3.0);
            assertThat(measured.cvrSource()).contains("내 사이트");
            assertThat(measured.cvrLowPct()).isLessThan(3.0).isGreaterThan(2.0);
            assertThat(measured.cvrHighPct()).isGreaterThan(3.0).isLessThan(4.0);
            assertThat(measured.basis()).anySatisfy(b -> {
                assertThat(b.item()).isEqualTo("전환율");
                assertThat(b.grade()).isEqualTo("실측");
            });
            assertThat(measured.maxCpc()).isNull();

            // 전환 1건 매출 20,000원, 목표 수익률 400% 면 전환 1건에 5,000원, 클릭당 150원까지만 쓸 수 있다.
            // Stub 의 가장 싼 클릭도 이보다 비싸면 계획을 세우지 않고 이유를 알린다.
            assertThatThrownBy(() -> planService.propose(
                    new PlanRequest(SEEDS, GoalType.CONVERSIONS, 10L, null, 20_000L, null, 2_000L, 60L, 400.0)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("클릭 단가 한도(150원)");

            // 한도가 넉넉하지 않으면 한도 안의 클릭만 사고, 목표에 못 미친다고 알린다.
            PlanProposal limited = planService.propose(
                    new PlanRequest(SEEDS, GoalType.CONVERSIONS, 1_000L, null, 100_000L, null, 2_000L, 60L, 400.0));
            assertThat(limited.maxCpc()).isEqualTo(750);
            assertThat(limited.keywords()).filteredOn(PlanKeyword::selected)
                    .allSatisfy(k -> assertThat(k.expectedCpc()).isLessThanOrEqualTo(750));
            assertThat(limited.warnings()).anySatisfy(w -> assertThat(w).contains("클릭 단가 한도(750원)"));
        });
    }

    @Test
    void rejectsIncompleteGoals() {
        OwnerContext.runAs(8104L, () -> {
            assertThatThrownBy(() -> planService.propose(
                    new PlanRequest(SEEDS, GoalType.CONVERSIONS, null, null, null, null, null, null, null)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> planService.propose(
                    new PlanRequest(SEEDS, GoalType.BUDGET, null, 500L, null, null, null, null, null)))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> planService.propose(
                    new PlanRequest(List.of(" "), GoalType.BUDGET, null, 300_000L, null, null, null, null, null)))
                    .isInstanceOf(BadRequestException.class);
        });
    }

    @Test
    void briefingAdvisesSpendingMoreWhenConversionsAreCheapAndBudgetIsExhausted() {
        OwnerContext.runAs(8105L, () -> {
            adSyncService.sync(30);
            // 목표 전환당 비용과 월 예산을 아주 넉넉하게 잡으면 실적이 목표보다 훨씬 좋고 계획 예산도 남는다.
            planRepository.save(new MarketingPlan("CONVERSIONS", 900_000_000, 100, 1_000_000, "{}"));

            // Stub 캠페인의 일예산(50,000원)은 실제 지출보다 훨씬 작다. 일예산이 없는 캠페인이 있는 것으로 보고
            // 일예산 증액 대신 입찰·키워드 확장을 권한다.
            Briefing loose = briefingService.generate(BriefingPeriod.WEEKLY);
            assertThat(loose.verdict()).isEqualTo(Verdict.INCREASE);
            assertThat(loose.currentDailyBudget()).isEqualTo(50_000);
            assertThat(loose.suggestedDailyBudget()).isNull();
            assertThat(loose.verdictReason()).contains("일예산이 없는 캠페인");

            // 일예산을 실제 하루 평균 지출과 같게 맞추면 예산에 막힌 상태이므로 20% 증액을 권한다.
            LocalDate until = LocalDate.now().minusDays(1);
            long avgDailySpend = statRepository.aggregateByKeyword(until.minusDays(13), until).stream()
                    .mapToLong(r -> r.salesAmt()).sum() / 14;
            long budget = avgDailySpend / 1_000 * 1_000;
            transaction.executeWithoutResult(status -> campaignRepository.findAll()
                    .forEach(c -> c.update(c.getName(), c.getCampaignTp(), c.getStatus(), budget)));

            Briefing weekly = briefingService.generate(BriefingPeriod.WEEKLY);

            assertThat(weekly.id()).isEqualTo(loose.id());
            assertThat(weekly.verdict()).isEqualTo(Verdict.INCREASE);
            assertThat(weekly.currentDailyBudget()).isEqualTo(budget);
            assertThat(weekly.suggestedDailyBudget()).isEqualTo((budget * 12 / 10 + 999) / 1_000 * 1_000);
            assertThat(weekly.since()).isEqualTo(weekly.until().minusDays(6));
            assertThat(weekly.metrics()).extracting(PlannerDtos.Metric::label)
                    .contains("광고비", "클릭률", "전환율", "전환당 비용");
            assertThat(weekly.pacing()).isNotNull();
            assertThat(weekly.pacing().monthlyBudget()).isEqualTo(900_000_000);
            assertThat(weekly.narrative()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(weekly.generatedBy()).isEqualTo("rule-based");

            // 같은 기간을 다시 만들면 새로 쌓이지 않고 덮어쓴다.
            Briefing again = briefingService.generate(BriefingPeriod.WEEKLY);
            assertThat(again.id()).isEqualTo(weekly.id());

            Briefing daily = briefingService.generate(BriefingPeriod.DAILY);
            assertThat(daily.since()).isEqualTo(daily.until());

            // 월간 보고서는 최근 30일을 다루고, 키워드별 효율 계산이 붙는다.
            Briefing monthly = briefingService.generate(BriefingPeriod.MONTHLY);
            assertThat(monthly.since()).isEqualTo(monthly.until().minusDays(29));
            assertThat(monthly.headline()).startsWith("[월간]");
            assertThat(monthly.efficiencyBasis()).contains("목표 전환당 비용 1,000,000원");
            assertThat(monthly.efficiency()).isNotEmpty().allSatisfy(k -> {
                assertThat(k.maxCpc()).isPositive();
                assertThat(k.actualCpc()).isPositive();
            });
            assertThat(monthly.narrative()).anySatisfy(p -> assertThat(p).startsWith("키워드 효율"));
            assertThat(monthly.autopilotSummary()).isNull();
            assertThat(briefingService.recent()).extracting(Briefing::id)
                    .containsExactlyInAnyOrder(weekly.id(), daily.id(), monthly.id());
        });
    }

    @Test
    void briefingDoesNotAdviseSpendingMoreWhenAlreadyOverThePlannedBudget() {
        OwnerContext.runAs(8108L, () -> {
            adSyncService.sync(30);
            // 효율은 목표보다 훨씬 좋지만 월 예산 계획이 실제 지출보다 한참 작다.
            planRepository.save(new MarketingPlan("CONVERSIONS", 100_000, 10, 1_000_000, "{}"));

            Briefing weekly = briefingService.generate(BriefingPeriod.WEEKLY);

            assertThat(weekly.verdict()).isEqualTo(Verdict.HOLD);
            assertThat(weekly.suggestedDailyBudget()).isNull();
            assertThat(weekly.verdictReason()).contains("계획한 100,000원을 넘습니다");
        });
    }

    @Test
    void briefingAdvisesCuttingWasteWhenConversionsCostFarMoreThanTheTarget() {
        OwnerContext.runAs(8106L, () -> {
            adSyncService.sync(30);
            planRepository.save(new MarketingPlan("CONVERSIONS", 100_000, 1_000, 100, "{}"));

            Briefing weekly = briefingService.generate(BriefingPeriod.WEEKLY);

            assertThat(weekly.verdict()).isEqualTo(Verdict.DECREASE);
            assertThat(weekly.suggestedDailyBudget()).isEqualTo(40_000);
            assertThat(weekly.verdictReason()).contains("목표 100원");
        });
    }

    @Test
    void briefingHoldsWhenWithinTargetAndNeedsDataFirst() {
        OwnerContext.runAs(8107L, () -> {
            assertThatThrownBy(() -> briefingService.generate(BriefingPeriod.DAILY))
                    .isInstanceOf(BadRequestException.class);

            adSyncService.sync(30);
            // 계획이 없으면 최근 30일 평균이 기준이 되므로 평소와 비슷한 주는 유지 판단이 나온다.
            Briefing weekly = briefingService.generate(BriefingPeriod.WEEKLY);
            assertThat(weekly.verdict()).isEqualTo(Verdict.HOLD);
            assertThat(weekly.pacing()).isNull();
            assertThat(weekly.verdictReason()).contains("최근 30일 평균");
        });
    }
}
