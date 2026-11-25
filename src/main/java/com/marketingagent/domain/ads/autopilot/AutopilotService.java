package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.account.AccountScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.common.security.SystemTask;
import com.marketingagent.domain.ads.AdCampaign;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.BidRecommendation;
import com.marketingagent.domain.ads.BidRecommendationRepository;
import com.marketingagent.domain.ads.BidRecommendationService;
import com.marketingagent.domain.ads.BidStatus;
import com.marketingagent.domain.ads.EfficiencyBidService;
import com.marketingagent.domain.ads.EfficiencyBidService.Analysis;
import com.marketingagent.domain.ads.EfficiencyBidService.KeywordEfficiency;
import com.marketingagent.domain.ads.EfficiencyBidService.Targets;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.DelegationRequest;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Settings;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.SettingsRequest;
import com.marketingagent.domain.ads.dto.BidApplyResult;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.planner.MarketingPlan;
import com.marketingagent.domain.ads.planner.MarketingPlanRepository;
import com.marketingagent.integration.naver.SearchAdClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자동 운영. 회원이 켜 두면 사람 대신 입찰가·키워드·일예산을 조정하고, 한 일을 기록으로 남긴다.
 *
 * <p>맡기는 범위는 좁고 한도는 고정이다.
 * <ul>
 *   <li>입찰가: 광고비 대비 효율 계산({@link EfficiencyBidService})이 낸 값만, 1회 변동폭·상하한·하루 건수 한도 안에서 바꾼다.</li>
 *   <li>키워드 중지: 전환 없이 전환 1건에 쓸 수 있는 돈의 3배 이상 쓴 키워드만, 한 번에 5개까지 끈다.</li>
 *   <li>일예산: 이번 달 한도를 넘기지 않도록 낮추고, 효율이 좋으면서 일예산을 거의 다 쓸 때만 20% 올린다. 한도는 절대 넘지 않는다.</li>
 *   <li>같은 키워드의 입찰가와 일예산 증액은 한 번 바꾸면 7일 동안 다시 바꾸지 않는다. 바꾼 효과를 확인한 뒤에 다음 판단을 한다.</li>
 * </ul>
 * 전환이 잡히지 않거나 클릭이 모자라면 아무것도 바꾸지 않는다. 판단할 근거가 없는 상태에서 움직이는 것이 가장 위험하다.
 * 키워드 추가, 제외 키워드, 광고 문구는 건드리지 않는다. 업종을 아는 사람이 봐야 하는 일이다.
 *
 * <p>단계마다 따로 저장한다. 한 단계가 실패해도 앞에서 이미 네이버에 반영한 변경의 기록이 사라지면 안 되기 때문이다.
 */
@Slf4j
@Service
public class AutopilotService {

    private static final int JUDGE_DAYS = 14;
    private static final long MIN_CLICKS = 100;
    private static final double GOOD_CPA_RATIO = 0.8;
    private static final double BUDGET_LIMITED_RATIO = 0.85;
    private static final double BUDGET_STEP_UP = 1.2;
    private static final double BUDGET_DEAD_BAND = 0.05;
    private static final long BUDGET_UNIT = 1_000;
    private static final long MIN_DAILY_BUDGET = 1_000;
    private static final int MAX_PAUSES_PER_RUN = 5;
    /** 일예산을 올린 뒤 다시 올리기까지 기다리는 날수. 매일 20%씩 복리로 불어나는 것을 막는다. */
    private static final int BUDGET_RAISE_COOLDOWN_DAYS = 7;

    static final String STATUS_APPLIED = "반영됨";
    static final String STATUS_DRY_RUN = "기록만";
    static final String STATUS_PREVIEW = "미리보기";
    static final String STATUS_FAILED = "실패";

    private final AutopilotSettingsRepository settingsRepository;
    private final AutopilotRunRepository runRepository;
    private final MarketingPlanRepository planRepository;
    private final EfficiencyBidService efficiencyBidService;
    private final BidRecommendationService bidService;
    private final BidRecommendationRepository recommendationRepository;
    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdCampaignRepository campaignRepository;
    private final AdAccountService accountService;
    private final BidPolicyProperties policy;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final AutopilotGoals goals;
    private final KeywordCurator keywordCurator;

    public AutopilotService(AutopilotSettingsRepository settingsRepository, AutopilotRunRepository runRepository,
            MarketingPlanRepository planRepository, EfficiencyBidService efficiencyBidService,
            BidRecommendationService bidService, BidRecommendationRepository recommendationRepository,
            KeywordStatDailyRepository statRepository, AdKeywordRepository keywordRepository,
            AdCampaignRepository campaignRepository, AdAccountService accountService, BidPolicyProperties policy,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager, AutopilotGoals goals,
            KeywordCurator keywordCurator) {
        this.goals = goals;
        this.keywordCurator = keywordCurator;
        this.settingsRepository = settingsRepository;
        this.runRepository = runRepository;
        this.planRepository = planRepository;
        this.efficiencyBidService = efficiencyBidService;
        this.bidService = bidService;
        this.recommendationRepository = recommendationRepository;
        this.statRepository = statRepository;
        this.keywordRepository = keywordRepository;
        this.campaignRepository = campaignRepository;
        this.accountService = accountService;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // --- 설정 ---

    public Settings settings(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        return toDto(scope, settingsRepository.findByAdAccountId(scope.id()).orElse(null));
    }

    public Settings updateSettings(Long accountId, SettingsRequest request) {
        AccountScope scope = accountService.scope(accountId);
        return tx.execute(status -> {
            AutopilotSettings settings = settingsRepository.findByAdAccountId(scope.id())
                    .orElseGet(() -> settingsRepository.save(new AutopilotSettings(scope.id())));
            settings.update(request.enabled(), request.targetCpa(), request.targetRoasPct(),
                    request.monthlyBudgetCap(), request.manageBids(), request.manageBudget(),
                    request.pauseWasteful());
            log.info("자동 운영 설정 변경: enabled={} bids={} budget={} pause={}", request.enabled(),
                    request.manageBids(), request.manageBudget(), request.pauseWasteful());
            return toDto(scope, settings);
        });
    }

    /** 효율 계산의 기준. 자동 운영 설정 → 운영 계획 → 서버 설정 순으로 찾는다. */
    public Targets targets(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        AutopilotSettings settings = settingsRepository.findByAdAccountId(scope.id()).orElse(null);
        MarketingPlan plan = planRepository.findTopByAdAccountIdOrderByIdDesc(scope.id()).orElse(null);
        double roas = settings != null ? settings.getTargetRoasPct() : 0.0;
        if (settings != null && settings.getTargetCpa() > 0) {
            return new Targets(settings.getTargetCpa(), roas, "자동 운영 설정");
        }
        if (plan != null && plan.getTargetCpa() > 0) {
            return new Targets(plan.getTargetCpa(), roas, "운영 계획의 목표");
        }
        // 초보 업체는 상담만 하고 제안안을 확정하지 않는 경우가 많다. 상담에서 계산한 목표를 그대로 쓴다.
        long goalCpa = goals.of(scope.id()).targetCpa();
        if (goalCpa > 0) {
            return new Targets(goalCpa, roas, "목표 상담 결과");
        }
        if (policy.targetCpa() > 0) {
            return new Targets(policy.targetCpa(), roas, "서버 설정");
        }
        return new Targets(0, roas, roas > 0 ? "자동 운영 설정" : "목표 없음");
    }

    /**
     * 일임 범위를 바꾼다. 지켜보기를 켜면 오늘부터 {@code observeDays} 일 동안은 판단과 기록만 한다.
     */
    public Settings updateDelegation(Long accountId, DelegationRequest request) {
        AccountScope scope = accountService.scope(accountId);
        return tx.execute(status -> {
            AutopilotSettings settings = settingsRepository.findByAdAccountId(scope.id())
                    .orElseGet(() -> settingsRepository.save(new AutopilotSettings(scope.id())));
            LocalDate observeUntil = request.observeDays() > 0
                    ? LocalDate.now().plusDays(request.observeDays() - 1L) : null;
            settings.updateDelegation(request.addKeywords(), request.addNegatives(), request.cleanupKeywords(),
                    request.realtimeBids(), request.targetRank(), request.maxCpc(), observeUntil);
            log.info("자동 운영 일임 범위 변경: 계정 {} 추가={} 제외={} 정리={} 실시간={} 순위={} 지켜보기={}", scope.id(),
                    request.addKeywords(), request.addNegatives(), request.cleanupKeywords(), request.realtimeBids(),
                    request.targetRank(), observeUntil);
            return toDto(scope, settings);
        });
    }

    /** 이 계정에서 지금 네이버에 반영하지 않고 기록만 하는지(서버 시험 모드 또는 지켜보기 기간). */
    public boolean recordOnly(Long accountId) {
        if (policy.dryRun()) {
            return true;
        }
        return settingsRepository.findByAdAccountId(accountId)
                .map(s -> s.isObserving(LocalDate.now()))
                .orElse(false);
    }

    // --- 실행 ---

    /** 일 배치에서 부른다. 자동 운영을 켜 둔 회원만 실행한다. */
    public Optional<RunResult> runIfEnabled(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        boolean enabled = settingsRepository.findByAdAccountId(scope.id()).map(AutopilotSettings::isEnabled).orElse(false);
        return enabled ? Optional.of(run(scope.id(), false)) : Optional.empty();
    }

    /**
     * @param preview true 면 무엇을 할지만 계산하고 아무것도 바꾸지 않는다
     */
    public RunResult run(Long accountId, boolean preview) {
        AccountScope scope = accountService.scope(accountId);
        AutopilotSettings settings = settingsRepository.findByAdAccountId(scope.id()).orElse(null);
        boolean manageBids = settings == null || settings.isManageBids();
        boolean manageBudget = settings == null || settings.isManageBudget();
        boolean pauseWasteful = settings == null || settings.isPauseWasteful();
        if (!preview && (settings == null || !settings.isEnabled())) {
            throw new BadRequestException("자동 운영이 꺼져 있습니다. 설정에서 켠 뒤 실행하세요.");
        }

        List<Action> actions = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Targets targets = targets(scope.id());
        boolean observing = settings != null && settings.isObserving(LocalDate.now());
        boolean dry = policy.dryRun() || observing;
        String idle = preview ? STATUS_PREVIEW : dry ? STATUS_DRY_RUN : STATUS_APPLIED;
        if (observing && !preview) {
            notes.add(String.format("%s까지는 지켜보기 기간이라 판단과 기록만 하고 네이버에는 반영하지 않습니다.",
                    settings.getObserveUntil()));
        }

        Analysis analysis = null;
        try {
            analysis = efficiencyBidService.analyze(scope.id(), targets);
        } catch (BadRequestException e) {
            boolean realtime = settings == null || settings.isRealtimeBids();
            notes.add("전환 효율로는 아직 판단하지 않았습니다. " + e.getMessage()
                    + (realtime ? " 그동안은 실시간 입찰이 낮 동안 1시간마다 목표 순위에 맞춰 입찰가를 조정합니다." : ""));
        }

        Set<String> paused = new HashSet<>();
        if (analysis != null && pauseWasteful) {
            pauseWasteful(scope, analysis, preview, dry, idle, actions, notes, paused);
        }
        if (analysis != null && manageBids) {
            adjustBids(scope, analysis, targets, preview, observing, paused, actions, notes);
        }
        if (manageBudget) {
            adjustBudget(scope, settings, targets, preview, dry, idle, actions, notes);
        }
        // 키워드 추가·제외 키워드·정리. 판단은 업종을 아는 AI 가 하고, 하루 건수는 좁게 묶는다.
        try {
            keywordCurator.curate(scope, settings, preview, dry, idle, actions, notes);
        } catch (RuntimeException e) {
            log.error("자동 운영 키워드 관리 실패: 계정 {}", scope.id(), e);
            notes.add("키워드 추가·제외·정리 중 오류가 나 이번에는 건너뛰었습니다: " + e.getMessage());
        }
        if (!manageBids && !manageBudget && !pauseWasteful && settings != null && !settings.isAddKeywords()
                && !settings.isAddNegatives() && !settings.isCleanupKeywords() && !settings.isRealtimeBids()) {
            notes.add("맡긴 항목이 없습니다. 설정에서 하나 이상을 켜세요.");
        }

        String summary = summary(actions, preview, dry);
        RunResult draft = new RunResult(null, preview, dry, summary, actions, notes, LocalDateTime.now());
        AutopilotRun saved = tx.execute(status -> runRepository.save(
                new AutopilotRun(scope.id(), LocalDate.now(), preview, dry, summary, toJson(draft))));
        log.info("자동 운영 {}: 계정 {} {}", preview ? "미리보기" : "실행", scope.id(), summary);
        return withId(draft, saved.getId());
    }

    public List<RunResult> recentRuns(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        return runRepository.findTop30ByAdAccountIdOrderByIdDesc(scope.id()).stream()
                .map(run -> withId(fromJson(run.getContentJson()), run.getId()))
                .toList();
    }

    /** 오늘 실제로 실행한 내용을 하나로 합친 것. 브리핑에 "자동 운영이 한 일"을 적을 때 쓴다. 하루에 여러 번 돌았으면 모두 합친다. */
    public Optional<RunResult> todaysRun(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        List<RunResult> runs = runRepository.findByAdAccountIdAndPreviewFalseAndRunDateOrderByIdAsc(scope.id(), LocalDate.now()).stream()
                .map(run -> withId(fromJson(run.getContentJson()), run.getId()))
                .toList();
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        List<Action> actions = runs.stream().flatMap(run -> run.actions().stream()).toList();
        List<String> notes = runs.stream().flatMap(run -> run.notes().stream()).distinct().toList();
        RunResult last = runs.get(runs.size() - 1);
        return Optional.of(new RunResult(last.id(), false, last.dryRun(), summary(actions, false, last.dryRun()),
                actions, notes, last.ranAt()));
    }

    // --- 단계 ---

    private void pauseWasteful(AccountScope scope, Analysis analysis, boolean preview, boolean dry, String idle,
            List<Action> actions, List<String> notes, Set<String> paused) {
        List<KeywordEfficiency> wasteful = analysis.keywords().stream()
                .filter(k -> "중지 검토".equals(k.verdict()))
                .toList();
        int handled = 0;
        for (KeywordEfficiency k : wasteful) {
            AdKeyword keyword = keywordRepository.findByAdAccountIdAndNccKeywordId(scope.id(), k.nccKeywordId()).orElse(null);
            if (keyword == null || "PAUSED".equals(keyword.getStatus())) {
                continue;
            }
            if (handled >= MAX_PAUSES_PER_RUN) {
                notes.add(String.format("전환 없이 돈만 쓰는 키워드가 %d개 더 있습니다. 한 번에 %d개까지만 끄므로 나머지는 다음 실행에서 처리합니다.",
                        wasteful.size() - handled, MAX_PAUSES_PER_RUN));
                break;
            }
            handled++;
            paused.add(k.nccKeywordId());
            String status = idle;
            if (!preview && !dry) {
                try {
                    scope.client().setKeywordPaused(keyword.getNccKeywordId(), keyword.getNccAdgroupId(), true);
                    tx.executeWithoutResult(s -> keywordRepository.findByAdAccountIdAndNccKeywordId(scope.id(), k.nccKeywordId())
                            .ifPresent(found -> found.update(found.getKeyword(), "PAUSED", found.getBidAmt(),
                                    found.getUseGroupBidAmt())));
                } catch (RuntimeException e) {
                    log.error("자동 운영 키워드 중지 실패: {}", k.keyword(), e);
                    status = STATUS_FAILED;
                }
            }
            actions.add(new Action("키워드 중지", k.keyword(), "노출 중", "중지",
                    String.format("최근 %d일 클릭 %,d회, 광고비 %,d원, 전환 0건. %s",
                            EfficiencyBidService.WINDOW_DAYS, k.clicks(), k.cost(), k.reason()), status));
        }
    }

    private void adjustBids(AccountScope scope, Analysis analysis, Targets targets, boolean preview, boolean observing,
            Set<String> paused, List<Action> actions, List<String> notes) {
        if (preview || observing) {
            // 미리보기·지켜보기는 추천도 승인하지 않는다. 계산 결과만 남긴다.
            String status = preview ? STATUS_PREVIEW : STATUS_DRY_RUN;
            analysis.keywords().stream()
                    .filter(k -> k.suggestedBid() != k.currentBid() && !paused.contains(k.nccKeywordId()))
                    .forEach(k -> actions.add(new Action("입찰가", k.keyword(), won(k.currentBid()),
                            won(k.suggestedBid()), k.reason(), status)));
            return;
        }
        List<BidRecommendation> recommendations = efficiencyBidService.generate(scope.id(), targets);
        long appliedToday = recommendationRepository.countByAdAccountIdAndStatusAndDecidedAtAfter(scope.id(), 
                BidStatus.APPLIED, LocalDate.now().atStartOfDay());
        long room = Math.max(0, policy.maxDailyApplies() - appliedToday);
        int skipped = 0;

        for (BidRecommendation r : recommendations) {
            if (paused.contains(r.getNccKeywordId())) {
                continue; // 끄기로 한 키워드는 입찰가를 바꿀 이유가 없다
            }
            if (room <= 0) {
                skipped++;
                continue;
            }
            room--;
            BidApplyResult result = bidService.approve(r.getId(), actor());
            boolean applied = "APPLIED".equals(result.status());
            actions.add(new Action("입찰가", r.getKeyword(), won(r.getCurrentBid()),
                    won(result.appliedBid() != null ? result.appliedBid() : r.getRecommendedBid()), r.getReason(),
                    !applied ? STATUS_FAILED : policy.dryRun() ? STATUS_DRY_RUN : STATUS_APPLIED));
        }
        if (skipped > 0) {
            notes.add(String.format("하루 입찰가 변경 한도(%d건)에 걸려 %d건은 내일로 미뤘습니다.", policy.maxDailyApplies(), skipped));
        }
    }

    /** 이번 달 한도를 지키는 것이 먼저고, 올리는 것은 효율이 확인됐을 때만 한다. */
    private void adjustBudget(AccountScope scope, AutopilotSettings settings, Targets targets, boolean preview,
            boolean dry, String idle, List<Action> actions, List<String> notes) {
        long cap = monthlyCap(scope, settings);
        if (cap <= 0) {
            notes.add("한 달 광고비 한도가 정해져 있지 않아 일예산은 건드리지 않았습니다. 운영 계획을 받거나 설정에서 한도를 정하세요.");
            return;
        }
        List<AdCampaign> campaigns = campaignRepository.findByAdAccountIdOrderByNameAsc(scope.id()).stream()
                .filter(c -> c.getDailyBudget() != null && c.getDailyBudget() > 0)
                .toList();
        long current = campaigns.stream().mapToLong(AdCampaign::getDailyBudget).sum();
        if (current <= 0) {
            notes.add("일예산이 설정된 캠페인이 없어 지출을 제한할 수 없습니다. 네이버 광고 관리에서 캠페인에 일예산을 먼저 걸어 주세요.");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        long spent = today.getDayOfMonth() == 1 ? 0
                : sum(statRepository.aggregateByKeyword(scope.id(), today.withDayOfMonth(1), yesterday))[1];
        int remainingDays = today.lengthOfMonth() - today.getDayOfMonth() + 1;
        long allowed = Math.max(0, cap - spent) / remainingDays / BUDGET_UNIT * BUDGET_UNIT;

        long desired = current;
        String reason = null;
        if (current > allowed) {
            desired = Math.max(allowed, MIN_DAILY_BUDGET * campaigns.size());
            reason = String.format("이번 달 한도 %,d원 중 %,d원을 썼고 %d일이 남았습니다. 남은 기간 하루 %,d원까지 쓸 수 있어 일예산을 낮춥니다.",
                    cap, spent, remainingDays, allowed);
            if (allowed < MIN_DAILY_BUDGET * campaigns.size()) {
                notes.add(String.format("이번 달 한도 %,d원을 거의 다 썼습니다(지출 %,d원). 일예산을 최저로 낮췄지만 완전히 멈추지는 않습니다. "
                        + "더 쓰지 않으려면 네이버 광고 관리에서 캠페인을 직접 꺼 주세요.", cap, spent));
            }
        } else {
            long[] judge = sum(statRepository.aggregateByKeyword(scope.id(), yesterday.minusDays(JUDGE_DAYS - 1L), yesterday));
            long clicks = judge[0];
            long cost = judge[1];
            long conversions = judge[2];
            double avgDailySpend = cost / (double) JUDGE_DAYS;
            boolean efficient = targets.targetCpa() > 0 && clicks >= MIN_CLICKS && conversions > 0
                    && cost / (double) conversions <= targets.targetCpa() * GOOD_CPA_RATIO;
            boolean limited = avgDailySpend >= current * BUDGET_LIMITED_RATIO && avgDailySpend <= current * 1.1;
            if (efficient && limited && budgetChangedRecently(scope)) {
                notes.add(String.format("효율이 좋고 일예산도 거의 다 쓰고 있지만, 최근 %d일 안에 일예산을 바꿨으므로 결과를 더 지켜봅니다.",
                        BUDGET_RAISE_COOLDOWN_DAYS));
            } else if (efficient && limited) {
                desired = Math.min((long) Math.ceil(current * BUDGET_STEP_UP / BUDGET_UNIT) * BUDGET_UNIT, allowed);
                reason = String.format("최근 %d일 전환당 비용 %,d원으로 목표 %,d원보다 20%% 이상 싸고, 하루 평균 %,d원을 써서 일예산을 거의 다 씁니다. "
                                + "이번 달 한도 안에서 올립니다.",
                        JUDGE_DAYS, cost / conversions, targets.targetCpa(), (long) avgDailySpend);
            }
        }
        if (reason == null || Math.abs(desired - current) < current * BUDGET_DEAD_BAND) {
            return;
        }

        for (AdCampaign campaign : campaigns) {
            long before = campaign.getDailyBudget();
            long after = Math.max(MIN_DAILY_BUDGET, before * desired / current / BUDGET_UNIT * BUDGET_UNIT);
            if (after == before) {
                continue;
            }
            String status = idle;
            if (!preview && !dry) {
                try {
                    long applied = scope.client().updateCampaignDailyBudget(campaign.getNccCampaignId(), after);
                    tx.executeWithoutResult(s -> campaignRepository.findByAdAccountIdAndNccCampaignId(scope.id(), campaign.getNccCampaignId())
                            .ifPresent(found -> found.update(found.getName(), found.getCampaignTp(),
                                    found.getStatus(), applied)));
                } catch (RuntimeException e) {
                    log.error("자동 운영 일예산 변경 실패: {}", campaign.getName(), e);
                    status = STATUS_FAILED;
                }
            }
            actions.add(new Action("일예산", campaign.getName(), won(before), won(after), reason, status));
        }
    }

    private boolean budgetChangedRecently(AccountScope scope) {
        LocalDateTime since = LocalDate.now().minusDays(BUDGET_RAISE_COOLDOWN_DAYS).atStartOfDay();
        return runRepository.findTop30ByAdAccountIdOrderByIdDesc(scope.id()).stream()
                .filter(run -> !run.isPreview())
                .map(run -> fromJson(run.getContentJson()))
                .filter(run -> run.ranAt() != null && run.ranAt().isAfter(since))
                .flatMap(run -> run.actions().stream())
                .anyMatch(a -> "일예산".equals(a.type()) && !STATUS_FAILED.equals(a.status()));
    }

    /** 승인 기록에 남길 주체. 일 배치에서는 시스템, 화면에서 실행하면 실행한 사람. */
    private static long actor() {
        return SystemTask.active() ? SystemTask.SYSTEM_ACTOR : CurrentUser.id();
    }

    /** 한 달 광고비 한도. 자동 운영 설정 → 운영 계획 → 목표 상담에서 말한 상한 순으로 찾는다. */
    long monthlyCap(AccountScope scope, AutopilotSettings settings) {
        if (settings != null && settings.getMonthlyBudgetCap() > 0) {
            return settings.getMonthlyBudgetCap();
        }
        long planned = planRepository.findTopByAdAccountIdOrderByIdDesc(scope.id())
                .map(MarketingPlan::getMonthlyBudget).orElse(0L);
        return planned > 0 ? planned : goals.of(scope.id()).budgetLimit();
    }

    /** 한 달 광고비 한도. 리포트와 실시간 입찰이 쓴다. */
    public long monthlyCap(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        return monthlyCap(scope, settingsRepository.findByAdAccountId(scope.id()).orElse(null));
    }

    // --- 보조 ---

    /** 조치 목록을 한 줄로. 실시간 입찰 기록에도 쓴다. */
    static String summary(List<Action> actions, boolean preview, boolean dry) {
        if (actions.isEmpty()) {
            return preview ? "지금 실행해도 바꿀 것이 없습니다." : "바꾼 것이 없습니다.";
        }
        long failed = actions.stream().filter(a -> STATUS_FAILED.equals(a.status())).count();
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        actions.forEach(a -> counts.merge(a.type(), 1L, Long::sum));
        List<String> parts = new ArrayList<>();
        counts.forEach((type, count) -> parts.add(type + " " + count + "건"));
        String tail = preview ? " 바꿀 예정입니다."
                : dry ? " 바꾸려 했지만 시험 모드(기록만)라 네이버에는 반영하지 않았습니다." : " 바꿨습니다.";
        return String.join(", ", parts) + tail + (failed > 0 ? " 그중 " + failed + "건은 실패했습니다." : "");
    }

    /** @return [클릭, 광고비, 전환] */
    private long[] sum(List<KeywordAggregate> rows) {
        long[] total = new long[3];
        for (KeywordAggregate row : rows) {
            total[0] += row.clkCnt() != null ? row.clkCnt() : 0;
            total[1] += row.salesAmt() != null ? row.salesAmt() : 0;
            total[2] += row.ccnt() != null ? row.ccnt() : 0;
        }
        return total;
    }

    private Settings toDto(AccountScope scope, AutopilotSettings s) {
        Targets targets = targets(scope.id());
        return new Settings(s != null && s.isEnabled(), s != null ? s.getTargetCpa() : 0,
                s != null ? s.getTargetRoasPct() : 0.0, s != null ? s.getMonthlyBudgetCap() : 0,
                s == null || s.isManageBids(), s == null || s.isManageBudget(), s == null || s.isPauseWasteful(),
                targets.targetCpa(), monthlyCap(scope, s), targets.source(), policy.dryRun(),
                Math.round(policy.maxChangeRate() * 1000) / 10.0, policy.maxDailyApplies(), policy.minBid(),
                policy.maxBid(),
                s == null || s.isAddKeywords(), s == null || s.isAddNegatives(), s == null || s.isCleanupKeywords(),
                s == null || s.isRealtimeBids(), s != null ? s.getTargetRank() : 3, s != null ? s.getMaxCpc() : 0,
                goals.maxCpc(scope.id(), s), s != null ? s.getObserveUntil() : null,
                policy.dryRun() || (s != null && s.isObserving(LocalDate.now())));
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private RunResult withId(RunResult r, Long id) {
        return new RunResult(id, r.preview(), r.dryRun(), r.summary(), r.actions(), r.notes(), r.ranAt());
    }

    private String toJson(RunResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("자동 운영 기록을 저장하지 못했습니다", e);
        }
    }

    private RunResult fromJson(String json) {
        try {
            return objectMapper.readValue(json, RunResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 자동 운영 기록을 읽지 못했습니다", e);
        }
    }
}
