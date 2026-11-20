package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.support.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.AdSyncService;
import com.marketingagent.domain.ads.BidRecommendationService;
import com.marketingagent.domain.ads.EfficiencyBidService;
import com.marketingagent.domain.ads.EfficiencyBidService.Analysis;
import com.marketingagent.domain.ads.EfficiencyBidService.KeywordEfficiency;
import com.marketingagent.domain.ads.EfficiencyBidService.Targets;
import com.marketingagent.domain.ads.EfficiencyMath;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Settings;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.SettingsRequest;
import com.marketingagent.domain.ads.planner.BriefingService;
import com.marketingagent.domain.ads.planner.PlannerDtos.Briefing;
import com.marketingagent.domain.ads.planner.PlannerDtos.BriefingPeriod;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stub 데이터로 효율 계산과 자동 운영의 한도를 확인한다. 회원마다 다른 소유자 ID 를 쓴다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:autopilot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=stub",
        "ads.bid.dry-run=false"
})
class AutopilotTest {

    @Autowired
    private TestAccounts accounts;

    /** Stub 계정의 전환당 비용은 8,000원 안팎이다. 목표를 그 절반으로 잡으면 모든 키워드가 비싸게 사고 있는 상태가 된다. */
    private static final long STRICT_CPA = 4_000;

    @Autowired
    private AutopilotService autopilotService;
    @Autowired
    private EfficiencyBidService efficiencyBidService;
    @Autowired
    private BidRecommendationService bidService;
    @Autowired
    private BriefingService briefingService;
    @Autowired
    private AdSyncService adSyncService;
    @Autowired
    private AdKeywordRepository keywordRepository;
    @Autowired
    private AdCampaignRepository campaignRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void efficiencyMathIsCautiousWithSmallSamples() {
        // 100번 중 3건과 10,000번 중 300건은 같은 3%지만 믿을 수 있는 정도가 다르다.
        double[] few = EfficiencyMath.wilson(3, 100, EfficiencyMath.Z90);
        double[] many = EfficiencyMath.wilson(300, 10_000, EfficiencyMath.Z90);
        assertThat(few[0]).isLessThan(many[0]);
        assertThat(few[1]).isGreaterThan(many[1]);
        assertThat(many[0]).isBetween(0.027, 0.03);
        assertThat(many[1]).isBetween(0.03, 0.033);

        // 클릭 20회에 전환 0건인 키워드를 전환율 0% 로 보지 않는다. 계정 평균(5%) 쪽으로 당긴다.
        assertThat(EfficiencyMath.shrunkRate(0, 20, 0.05, 50)).isBetween(0.035, 0.036);
        // 클릭이 많이 쌓이면 키워드 자신의 값에 가까워진다.
        assertThat(EfficiencyMath.shrunkRate(0, 5_000, 0.05, 50)).isLessThan(0.001);

        // 전환 1건 매출 100,000원, 목표 수익률 400% → 전환 1건에 25,000원, 전환율 2% 면 클릭당 500원.
        assertThat(EfficiencyMath.allowableCpa(100_000, 400)).isEqualTo(25_000);
        assertThat(EfficiencyMath.maxCpc(0.02, 25_000)).isEqualTo(500);
    }

    @Test
    void calculatesBidsFromWhatAClickIsWorth() {
        accounts.runAs(8201L, acc -> {
            adSyncService.sync(acc, 30);

            Analysis strict = efficiencyBidService.analyze(acc, new Targets(STRICT_CPA, 0, "테스트"));
            assertThat(strict.mode()).isEqualTo("CPA");
            assertThat(strict.keywords()).hasSize(10).allSatisfy(k -> {
                // 클릭당 쓸 수 있는 돈 = 전환율 × 목표 전환당 비용
                assertThat(k.maxCpc()).isCloseTo(Math.round(k.cvrPct() / 100 * STRICT_CPA), org.assertj.core.data.Offset.offset(1L));
                assertThat(k.actualCpc()).isEqualTo(k.cost() / k.clicks());
                // 쓸 수 있는 돈보다 비싸게 사고 있으므로 내리되, 한 번에 20% 까지만, 10원 단위로.
                assertThat(k.maxCpc()).isLessThan(k.actualCpc());
                assertThat(k.verdict()).isEqualTo("내리기");
                assertThat(k.suggestedBid()).isLessThan(k.currentBid())
                        .isGreaterThanOrEqualTo(Math.round(k.currentBid() * 0.8) / 10 * 10);
                assertThat(k.suggestedBid() % 10).isZero();
            });

            // 목표가 넉넉하면 반대로 올릴 여유가 생긴다. 이미 2위 안이면 올리지 않는다.
            Analysis loose = efficiencyBidService.analyze(acc, new Targets(1_000_000, 0, "테스트"));
            assertThat(loose.keywords()).allSatisfy(k -> {
                assertThat(k.verdict()).isIn("올리기", "유지");
                assertThat(k.suggestedBid()).isLessThanOrEqualTo(Math.round(k.currentBid() * 1.2) + 5);
            });
            assertThat(loose.keywords()).anySatisfy(k -> assertThat(k.verdict()).isEqualTo("올리기"));

            // 목표가 없으면 계산하지 않는다.
            assertThatThrownBy(() -> efficiencyBidService.analyze(acc, new Targets(0, 0, "목표 없음")))
                    .isInstanceOf(BadRequestException.class);
        });
    }

    @Test
    void doesNothingUntilTheMemberTurnsItOnAndPreviewChangesNothing() {
        accounts.runAs(8202L, acc -> {
            adSyncService.sync(acc, 30);
            Map<String, Long> before = bids(acc);
            Map<String, Long> budgets = budgets(acc);

            assertThat(autopilotService.settings(acc).enabled()).isFalse();
            assertThat(autopilotService.runIfEnabled(acc)).isEmpty();
            assertThatThrownBy(() -> autopilotService.run(acc, false)).isInstanceOf(BadRequestException.class);

            // 꺼진 채로 목표만 정해 두고 미리보기를 본다.
            autopilotService.updateSettings(acc, new SettingsRequest(false, STRICT_CPA, 0, 300_000, true, true, true));
            RunResult preview = autopilotService.run(acc, true);

            assertThat(preview.preview()).isTrue();
            assertThat(preview.actions()).isNotEmpty()
                    .allSatisfy(a -> assertThat(a.status()).isEqualTo(AutopilotService.STATUS_PREVIEW));
            assertThat(preview.summary()).contains("바꿀 예정");
            assertThat(bids(acc)).isEqualTo(before);
            assertThat(bidService.findPending(acc, null)).isEmpty();
            assertThat(bidService.findHistory(acc, null)).isEmpty();
            assertThat(budgets(acc)).isEqualTo(budgets);
            assertThat(autopilotService.todaysRun(acc)).isEmpty();
        });
    }

    @Test
    void whenOnItAdjustsBidsPausesWasteAndKeepsTheMonthlyCap() {
        accounts.runAs(8203L, acc -> {
            adSyncService.sync(acc, 30);
            Map<String, Long> before = bids(acc);
            // 한 키워드는 전환이 전혀 없었던 것으로 만든다.
            String wasteId = keywordRepository.findByAdAccountId(acc).get(0).getNccKeywordId();
            jdbc.update("update keyword_stat_daily set ccnt = 0 where ad_account_id = ? and ncc_keyword_id = ?", acc, wasteId);

            Settings settings = autopilotService.updateSettings(acc, 
                    new SettingsRequest(true, STRICT_CPA, 0, 300_000, true, true, true));
            assertThat(settings.effectiveTargetCpa()).isEqualTo(STRICT_CPA);
            assertThat(settings.effectiveMonthlyBudgetCap()).isEqualTo(300_000);
            assertThat(settings.targetSource()).isEqualTo("자동 운영 설정");

            RunResult run = autopilotService.runIfEnabled(acc).orElseThrow();

            assertThat(run.preview()).isFalse();
            assertThat(run.dryRun()).isFalse();
            Map<String, List<Action>> byType = run.actions().stream().collect(Collectors.groupingBy(Action::type));

            // 전환 없이 돈만 쓴 키워드는 끈다. 그 키워드의 입찰가는 따로 바꾸지 않는다.
            assertThat(byType.get("키워드 중지")).hasSize(1);
            AdKeyword waste = keywordRepository.findByAdAccountIdAndNccKeywordId(acc, wasteId).orElseThrow();
            assertThat(waste.getStatus()).isEqualTo("PAUSED");
            assertThat(waste.getBidAmt()).isEqualTo(before.get(wasteId));

            // 나머지 9개는 입찰가를 내리되 한 번에 20% 를 넘지 않는다.
            assertThat(byType.get("입찰가")).hasSize(9)
                    .allSatisfy(a -> assertThat(a.status()).isEqualTo(AutopilotService.STATUS_APPLIED));
            bids(acc).forEach((id, bid) -> {
                if (!id.equals(wasteId)) {
                    assertThat(bid).isLessThan(before.get(id))
                            .isGreaterThanOrEqualTo(Math.round(before.get(id) * 0.8) / 10 * 10);
                    assertThat(bid % 10).isZero();
                }
            });
            assertThat(bidService.findHistory(acc, null)).hasSize(9);

            // 이번 달 한도(300,000원)를 넘지 않도록 일예산을 낮춘다. 예시 계정은 캠페인이 2개라 둘 다 낮춘다.
            assertThat(byType.get("일예산")).hasSize(2);
            assertThat(campaignRepository.findByAdAccountIdOrderByNameAsc(acc)).allSatisfy(c ->
                    assertThat(c.getDailyBudget()).isLessThanOrEqualTo(10_000).isGreaterThanOrEqualTo(1_000));
            assertThat(run.summary()).contains("입찰가 9건", "키워드 중지 1건", "일예산 2건");

            // 네이버 쪽(Stub)에도 실제로 반영됐다. 다시 받아 와도 그대로다.
            adSyncService.sync(acc, 1);
            assertThat(keywordRepository.findByAdAccountIdAndNccKeywordId(acc, wasteId).orElseThrow().getStatus()).isEqualTo("PAUSED");
            assertThat(campaignRepository.findByAdAccountIdOrderByNameAsc(acc)).allSatisfy(c ->
                    assertThat(c.getDailyBudget()).isLessThanOrEqualTo(10_000));

            // 바로 다시 돌려도 같은 키워드를 또 내리지 않는다. 바꾼 효과가 실적에 나타날 때까지 기다린다.
            Map<String, Long> afterFirst = bids(acc);
            RunResult second = autopilotService.run(acc, false);
            assertThat(second.actions()).isEmpty();
            assertThat(bids(acc)).isEqualTo(afterFirst);
            assertThat(efficiencyBidService.analyze(acc, autopilotService.targets(acc)).keywords())
                    .filteredOn(k -> !k.nccKeywordId().equals(wasteId))
                    .allSatisfy(k -> assertThat(k.verdict()).isEqualTo("지켜보는 중"));

            // 한 일은 기록으로 남고, 그날 브리핑에 보고된다.
            assertThat(autopilotService.recentRuns(acc)).extracting(RunResult::id).containsExactly(second.id(), run.id());
            Briefing daily = briefingService.generate(acc, BriefingPeriod.DAILY);
            assertThat(daily.autopilotSummary()).contains("입찰가 9건", "키워드 중지 1건");
            assertThat(daily.autopilotActions()).hasSameSizeAs(run.actions());
            assertThat(daily.narrative()).anySatisfy(p -> assertThat(p).startsWith("자동 운영이 오늘 한 일"));
            assertThat(daily.efficiency()).extracting(KeywordEfficiency::verdict).contains("중지됨", "지켜보는 중");
        });
        // 다른 회원의 설정과 기록에는 영향이 없다.
        accounts.runAs(8204L, acc -> {
            assertThat(autopilotService.settings(acc).enabled()).isFalse();
            assertThat(autopilotService.recentRuns(acc)).isEmpty();
        });
    }

    @Test
    void leavesBidsAloneWhenConversionsAreNotTracked() {
        accounts.runAs(8205L, acc -> {
            adSyncService.sync(acc, 30);
            jdbc.update("update keyword_stat_daily set ccnt = 0 where ad_account_id = ?", acc);
            Map<String, Long> before = bids(acc);
            autopilotService.updateSettings(acc, new SettingsRequest(true, STRICT_CPA, 0, 0, true, true, true));

            RunResult run = autopilotService.run(acc, false);

            // 전환이 안 잡히면 어떤 키워드가 돈을 버는지 모른다. 모르는 채로 입찰가를 움직이지 않는다.
            assertThat(run.actions()).isEmpty();
            assertThat(run.notes()).anySatisfy(n -> assertThat(n).contains("전환 추적"));
            assertThat(run.notes()).anySatisfy(n -> assertThat(n).contains("한도가 정해져 있지 않아"));
            assertThat(bids(acc)).isEqualTo(before);
            assertThat(keywordRepository.findByAdAccountId(acc)).allSatisfy(k -> assertThat(k.getStatus()).isEqualTo("ELIGIBLE"));
        });
    }

    private Map<String, Long> budgets(Long acc) {
        return campaignRepository.findByAdAccountIdOrderByNameAsc(acc).stream()
                .collect(Collectors.toMap(c -> c.getNccCampaignId(), c -> c.getDailyBudget()));
    }

    private Map<String, Long> bids(Long acc) {
        return keywordRepository.findByAdAccountId(acc).stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, AdKeyword::getBidAmt));
    }
}
