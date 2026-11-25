package com.marketingagent.domain.ads.autopilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.ads.AdGroupRepository;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.BidRecommendation;
import com.marketingagent.domain.ads.BidRecommendationRepository;
import com.marketingagent.domain.ads.BidStatus;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.account.AccountScope;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.integration.naver.dto.StatRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실시간 입찰. 낮 동안 1시간마다 키워드 입찰가를 목표 순위에 맞춘다.
 *
 * <p>경쟁사가 입찰가를 바꾸면 같은 입찰가라도 순위가 밀린다. 사람이 하루 종일 볼 수 없으니, 네이버의
 * "목표 순위 추정 입찰가"를 1시간마다 받아 그 값으로 따라간다. 전환 데이터가 없어도 동작하므로 막 시작한
 * 업체도 바로 쓸 수 있다.
 *
 * <p>지키는 선:
 * <ul>
 *   <li>클릭 1회 최대 금액({@link AutopilotGoals#maxCpc})을 절대 넘지 않는다. 근거가 없으면 아무것도 바꾸지 않는다.</li>
 *   <li>한 번에 바꾸는 폭은 입찰 정책의 1회 변동폭 안이다. 1시간마다 조금씩 따라간다.</li>
 *   <li>오늘 예산을 너무 빨리 쓰고 있으면(월 한도 기준) 입찰가를 낮춰 속도를 늦춘다.</li>
 *   <li>최근 7일 안에 전환 효율 기준으로 바꾼 키워드는 그 판단을 존중해 건드리지 않는다.</li>
 * </ul>
 */
@Slf4j
@Service
public class RealtimeBidService {

    static final String TYPE_REALTIME = "실시간 입찰";
    private static final int MAX_CHANGES_PER_RUN = 30;
    private static final int ESTIMATE_CHUNK = 50;
    private static final int RESPECT_EFFICIENCY_DAYS = 7;
    private static final double FAST_PACE = 1.3;
    private static final double SLOW_DOWN = 0.85;
    private static final double STOP_PACE = 0.7;

    private final AutopilotSettingsRepository settingsRepository;
    private final AutopilotRunRepository runRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;
    private final BidRecommendationRepository recommendationRepository;
    private final KeywordStatDailyRepository statRepository;
    private final AdAccountService accountService;
    private final AutopilotService autopilotService;
    private final AutopilotGoals goals;
    private final BidPolicyProperties policy;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public RealtimeBidService(AutopilotSettingsRepository settingsRepository, AutopilotRunRepository runRepository,
            AdKeywordRepository keywordRepository, AdGroupRepository adGroupRepository,
            BidRecommendationRepository recommendationRepository, KeywordStatDailyRepository statRepository,
            AdAccountService accountService, AutopilotService autopilotService, AutopilotGoals goals,
            BidPolicyProperties policy, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.runRepository = runRepository;
        this.keywordRepository = keywordRepository;
        this.adGroupRepository = adGroupRepository;
        this.recommendationRepository = recommendationRepository;
        this.statRepository = statRepository;
        this.accountService = accountService;
        this.autopilotService = autopilotService;
        this.goals = goals;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 배치에서 부른다. 자동 운영과 실시간 입찰을 모두 켠 계정만 돈다. 바꾼 것이 있을 때만 기록한다. */
    public Optional<RunResult> runIfEnabled(Long accountId) {
        AccountScope scope = accountService.scope(accountId);
        AutopilotSettings settings = settingsRepository.findByAdAccountId(scope.id()).orElse(null);
        if (settings == null || !settings.isEnabled() || !settings.isRealtimeBids()) {
            return Optional.empty();
        }
        return Optional.of(run(scope.id(), false));
    }

    /** @param preview true 면 계산만 하고 바꾸지도 기록하지도 않는다 */
    public RunResult run(Long accountId, boolean preview) {
        AccountScope scope = accountService.scope(accountId);
        AutopilotSettings settings = settingsRepository.findByAdAccountId(scope.id()).orElse(null);
        boolean dry = autopilotService.recordOnly(scope.id());
        String idle = preview ? AutopilotService.STATUS_PREVIEW
                : dry ? AutopilotService.STATUS_DRY_RUN : AutopilotService.STATUS_APPLIED;
        List<Action> actions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        long maxCpc = goals.maxCpc(scope.id(), settings);
        int targetRank = settings != null ? settings.getTargetRank() : 3;
        if (maxCpc <= 0) {
            notes.add("클릭 1회 최대 금액을 정할 근거가 없어 입찰가를 건드리지 않았습니다. 목표 상담을 마치거나 자동 운영 설정에서 최대 금액을 정해 주세요.");
            return finish(scope, preview, dry, actions, notes);
        }

        List<AdKeyword> keywords = keywordRepository.findByAdAccountId(scope.id()).stream()
                .filter(k -> k.getStatus() == null || !k.getStatus().toUpperCase().contains("PAUSE"))
                .filter(k -> !"DELETED".equalsIgnoreCase(k.getStatus()))
                .toList();
        if (keywords.isEmpty()) {
            notes.add("노출 중인 키워드가 없습니다.");
            return finish(scope, preview, dry, actions, notes);
        }

        Set<String> respected = recentlyTunedByEfficiency(scope.id());
        double pace = paceFactor(scope, keywords, notes);
        Map<String, Long> groupBids = new HashMap<>();
        adGroupRepository.findByAdAccountIdOrderByNameAsc(scope.id())
                .forEach(g -> groupBids.put(g.getNccAdgroupId(), g.getBidAmt() != null ? g.getBidAmt() : 0L));

        Map<String, Long> estimates = estimates(scope, keywords, targetRank, notes);
        int changed = 0;
        int skipped = 0;
        for (AdKeyword k : keywords) {
            if (respected.contains(k.getNccKeywordId())) {
                continue;
            }
            Long estimate = estimates.get(k.getKeyword());
            long current = Boolean.TRUE.equals(k.getUseGroupBidAmt()) || k.getBidAmt() == null
                    ? groupBids.getOrDefault(k.getNccAdgroupId(), 0L)
                    : k.getBidAmt();
            if (current <= 0) {
                continue;
            }
            long goal = estimate != null && estimate > 0 ? Math.min(estimate, maxCpc) : Math.min(current, maxCpc);
            long desired = roundTo10(goal * pace);
            long step = Math.max(10, Math.round(current * policy.maxChangeRate()));
            long next = Math.max(current - step, Math.min(current + step, desired));
            next = Math.max(policy.minBid(), Math.min(policy.maxBid(), Math.min(maxCpc, roundTo10(next))));
            if (Math.abs(next - current) < Math.max(10, current * 0.03)) {
                continue;
            }
            if (changed >= MAX_CHANGES_PER_RUN) {
                skipped++;
                continue;
            }
            changed++;
            String status = idle;
            long applied = next;
            if (!preview && !dry) {
                try {
                    applied = scope.client().updateKeywordBid(k.getNccKeywordId(), k.getNccAdgroupId(), k.getKeyword(), next);
                    long finalApplied = applied;
                    tx.executeWithoutResult(s -> keywordRepository.findByAdAccountIdAndNccKeywordId(scope.id(), k.getNccKeywordId())
                            .ifPresent(found -> found.update(found.getKeyword(), found.getStatus(), finalApplied, false)));
                } catch (RuntimeException e) {
                    log.error("실시간 입찰 변경 실패: {}", k.getKeyword(), e);
                    status = AutopilotService.STATUS_FAILED;
                }
            }
            actions.add(new Action(TYPE_REALTIME, k.getKeyword(), won(current), won(applied),
                    reason(estimate, targetRank, maxCpc, pace, current > maxCpc), status));
        }
        if (skipped > 0) {
            notes.add(String.format("바꿀 키워드가 %d개 더 있지만 한 번에 %d개까지만 바꿔 다음 시간에 이어서 맞춥니다.",
                    skipped, MAX_CHANGES_PER_RUN));
        }
        return finish(scope, preview, dry, actions, notes);
    }

    /**
     * 오늘 쓴 돈이 월 한도로 계산한 오늘 몫보다 빠르게 나가면 입찰가를 낮춘다.
     * 오늘 실적을 못 받거나 월 한도가 없으면 1(그대로).
     */
    private double paceFactor(AccountScope scope, List<AdKeyword> keywords, List<String> notes) {
        long cap = autopilotService.monthlyCap(scope.id());
        if (cap <= 0) {
            return 1.0;
        }
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        long spentMonth = 0;
        if (today.getDayOfMonth() > 1) {
            for (var row : statRepository.aggregateByKeyword(scope.id(), today.withDayOfMonth(1), yesterday)) {
                spentMonth += row.salesAmt() != null ? row.salesAmt() : 0;
            }
        }
        int remainingDays = today.lengthOfMonth() - today.getDayOfMonth() + 1;
        long todayAllowed = Math.max(0, cap - spentMonth) / remainingDays;
        long spentToday;
        try {
            spentToday = scope.client().dailyStats(keywords.stream().map(AdKeyword::getNccKeywordId).toList(), today, today)
                    .stream().mapToLong(StatRow::salesAmt).sum();
        } catch (RuntimeException e) {
            return 1.0;
        }
        LocalTime now = LocalTime.now();
        double elapsed = Math.max(0.1, (now.getHour() * 60 + now.getMinute()) / 1440.0);
        if (todayAllowed <= 0 || spentToday >= todayAllowed) {
            notes.add(String.format("오늘 쓸 몫 %,d원을 이미 다 썼습니다(오늘 %,d원). 입찰가를 30%% 낮춰 지출 속도를 줄입니다.",
                    todayAllowed, spentToday));
            return STOP_PACE;
        }
        double expected = todayAllowed * elapsed;
        if (spentToday > expected * FAST_PACE) {
            notes.add(String.format("오늘 지출(%,d원)이 이 시간까지 쓸 몫(%,d원)보다 빠릅니다. 입찰가를 15%% 낮춰 하루 예산이 저녁 전에 바닥나지 않게 합니다.",
                    spentToday, Math.round(expected)));
            return SLOW_DOWN;
        }
        return 1.0;
    }

    private Map<String, Long> estimates(AccountScope scope, List<AdKeyword> keywords, int rank, List<String> notes) {
        Map<String, Long> result = new HashMap<>();
        List<String> texts = keywords.stream().map(AdKeyword::getKeyword).distinct().toList();
        for (int i = 0; i < texts.size(); i += ESTIMATE_CHUNK) {
            try {
                result.putAll(scope.client().estimateBidForPosition(
                        texts.subList(i, Math.min(texts.size(), i + ESTIMATE_CHUNK)), rank, "MOBILE"));
            } catch (RuntimeException e) {
                log.warn("목표 순위 추정 입찰가 조회 실패: {}", e.getMessage());
                notes.add("네이버 추정 입찰가를 일부 받지 못해 그 키워드는 최대 금액 넘는 것만 낮췄습니다.");
            }
        }
        return result;
    }

    /** 최근 7일 안에 전환 효율 기준으로 바꾼 키워드. 그 판단이 결과를 낼 때까지 실시간 입찰은 손대지 않는다. */
    private Set<String> recentlyTunedByEfficiency(Long accountId) {
        Set<String> ids = new HashSet<>();
        recommendationRepository.findByAdAccountIdAndStatusAndDecidedAtAfter(accountId, BidStatus.APPLIED,
                        LocalDateTime.now().minusDays(RESPECT_EFFICIENCY_DAYS))
                .stream().map(BidRecommendation::getNccKeywordId).forEach(ids::add);
        return ids;
    }

    private RunResult finish(AccountScope scope, boolean preview, boolean dry, List<Action> actions, List<String> notes) {
        String summary = "[실시간] " + AutopilotService.summary(actions, preview, dry);
        RunResult draft = new RunResult(null, preview, dry, summary, actions, notes, LocalDateTime.now());
        if (preview || actions.isEmpty()) {
            return draft;
        }
        AutopilotRun saved = tx.execute(s -> runRepository.save(
                new AutopilotRun(scope.id(), LocalDate.now(), false, dry, summary, toJson(draft))));
        log.info("실시간 입찰: 계정 {} {}", scope.id(), summary);
        return new RunResult(saved.getId(), false, dry, summary, actions, notes, draft.ranAt());
    }

    private static String reason(Long estimate, int rank, long maxCpc, double pace, boolean overCeiling) {
        StringBuilder sb = new StringBuilder();
        if (overCeiling) {
            return String.format("지금 입찰가가 클릭 1회 최대 %,d원을 넘어서 바로 최대 금액으로 내립니다. "
                    + "이보다 비싸게 클릭을 사면 문의가 와도 손해입니다.", maxCpc);
        }
        if (estimate != null && estimate > 0) {
            sb.append(String.format("지금 %d위에 오르려면 약 %,d원이 필요합니다.", rank, estimate));
            if (estimate > maxCpc) {
                sb.append(String.format(" 클릭 1회 최대 %,d원을 넘어서 최대 금액까지만 씁니다.", maxCpc));
            }
        } else {
            sb.append(String.format("클릭 1회 최대 %,d원을 넘지 않게 맞춥니다.", maxCpc));
        }
        if (pace < 1.0) {
            sb.append(" 오늘 지출 속도가 빨라 조금 낮췄습니다.");
        }
        sb.append(" 한 번에 크게 바꾸지 않고 1시간마다 나눠서 따라갑니다.");
        return sb.toString();
    }

    private static long roundTo10(double value) {
        return Math.round(value / 10.0) * 10;
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private String toJson(RunResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("실시간 입찰 기록을 저장하지 못했습니다", e);
        }
    }
}
