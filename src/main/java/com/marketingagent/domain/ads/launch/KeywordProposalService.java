package com.marketingagent.domain.ads.launch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.ads.AdCampaign;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdGroup;
import com.marketingagent.domain.ads.AdGroupRepository;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.account.AccountScope;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.autopilot.AutopilotRun;
import com.marketingagent.domain.ads.autopilot.AutopilotRunRepository;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.domain.ads.launch.ProposalDtos.ApplyItem;
import com.marketingagent.domain.ads.launch.ProposalDtos.ApplyRequest;
import com.marketingagent.domain.ads.launch.ProposalDtos.Item;
import com.marketingagent.domain.ads.launch.ProposalDtos.Option;
import com.marketingagent.domain.ads.launch.ProposalDtos.Proposal;
import com.marketingagent.domain.ads.launch.ProposalDtos.ProposalRequest;
import com.marketingagent.domain.ads.launch.ProposalDtos.Summary;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 키워드 분석·제안. 네이버 키워드도구(검색량·클릭률·경쟁)와 입찰가 단계별 성과 예측으로
 * "어떤 키워드를 얼마에 걸면 한 달에 몇 번 클릭되고 얼마 드는지"를 키워드마다 보여 주고, 예산 안에서 추천안을 만든다.
 *
 * <p>분석은 네이버에 아무것도 쓰지 않는다. 사용자가 표에서 고른 키워드와 입찰가만 {@link #apply} 로 반영한다.
 * 반영 기록은 자동 운영 실행 기록에 "[키워드 제안]" 으로 남아, 실시간 입찰이 7일 동안 이 입찰가를 존중한다.
 */
@Slf4j
@Service
public class KeywordProposalService {

    public static final String RUN_PREFIX = "[키워드 제안]";
    private static final List<Long> LADDER = List.of(100L, 200L, 300L, 500L, 700L, 1000L, 1500L, 2000L, 2500L,
            3000L, 4000L, 5000L, 6000L, 8000L, 10000L, 15000L, 20000L);
    private static final int MAX_CANDIDATES = 60;
    private static final int HINT_BATCH = 5;
    private static final double AUDIENCE_WEIGHT = 1.5;
    private static final double FOCUS_WEIGHT = 1.3;

    private final AdAccountService accountService;
    private final AdCampaignRepository campaignRepository;
    private final AdGroupRepository adGroupRepository;
    private final AdKeywordRepository keywordRepository;
    private final AutopilotRunRepository runRepository;
    private final AutopilotService autopilotService;
    private final BidPolicyProperties policy;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public KeywordProposalService(AdAccountService accountService, AdCampaignRepository campaignRepository,
            AdGroupRepository adGroupRepository, AdKeywordRepository keywordRepository,
            AutopilotRunRepository runRepository, AutopilotService autopilotService, BidPolicyProperties policy,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.accountService = accountService;
        this.campaignRepository = campaignRepository;
        this.adGroupRepository = adGroupRepository;
        this.keywordRepository = keywordRepository;
        this.runRepository = runRepository;
        this.autopilotService = autopilotService;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // --- 분석 ---

    public Proposal analyze(Long accountId, ProposalRequest req) {
        AccountScope scope = accountService.scope(accountId);
        SearchAdClient client = scope.client();
        List<String> hints = clean(req.hints());
        if (hints.isEmpty()) {
            throw new BadRequestException("무엇을 파는지 보여 줄 힌트 키워드를 하나 이상 넣어 주세요.");
        }

        AdCampaign campaign = req.nccCampaignId() == null || req.nccCampaignId().isBlank() ? null
                : campaignRepository.findByAdAccountIdAndNccCampaignId(scope.id(), req.nccCampaignId()).orElse(null);
        double cvr = (req.cvrPct() > 0 ? req.cvrPct() : 2.0) / 100.0;
        long profit = req.profitPerConversion() > 0 ? req.profitPerConversion()
                : campaign != null && campaign.getBreakEvenCpa() != null ? campaign.getBreakEvenCpa() : 0;
        long maxCpc = req.maxCpc() > 0 ? req.maxCpc()
                : campaign != null && campaign.getMaxCpc() != null ? campaign.getMaxCpc()
                : profit > 0 ? Math.round(profit * cvr / 10.0) * 10 : 3000;
        maxCpc = Math.max(policy.minBid(), Math.min(policy.maxBid(), maxCpc));
        long monthlyBudget = req.monthlyBudget() > 0 ? req.monthlyBudget()
                : campaign != null && campaign.getDailyBudget() != null && campaign.getDailyBudget() > 0
                ? campaign.getDailyBudget() * 30 : 1_500_000;

        KeywordSelector.Rules rules = new KeywordSelector.Rules(clean(req.typeTerms()), clean(req.focusTerms()),
                clean(req.excludeWords()), clean(req.audienceTerms()), clean(req.focusTerms()), AUDIENCE_WEIGHT,
                FOCUS_WEIGHT, 20);
        KeywordSelector.Budget budget = new KeywordSelector.Budget(monthlyBudget, maxCpc, profit, cvr,
                req.maxKeywords() > 0 ? req.maxKeywords() : 25);

        // 1) 후보: 키워드도구 연관 키워드 중 관련 있는 것, 검색량 순 상위
        Map<String, RelatedKeyword> pool = new LinkedHashMap<>();
        for (int i = 0; i < hints.size(); i += HINT_BATCH) {
            List<String> batch = hints.subList(i, Math.min(i + HINT_BATCH, hints.size()));
            try {
                for (RelatedKeyword r : client.relatedKeywords(batch)) {
                    String k = r.keyword().replace(" ", "");
                    if (KeywordSelector.relevant(k, rules)) {
                        pool.merge(k, r, (x, y) -> x.totalCount() >= y.totalCount() ? x : y);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("키워드도구 조회 실패 {}: {}", batch, e.getMessage());
            }
        }
        Map<String, RelatedKeyword> candidates = new LinkedHashMap<>();
        pool.values().stream()
                .sorted(Comparator.comparingLong(RelatedKeyword::totalCount).reversed())
                .limit(MAX_CANDIDATES)
                .forEach(r -> candidates.put(r.keyword().replace(" ", ""), r));

        // 2) 입찰가 단계별 예측 (최대 금액 이하 + 위로 몇 단계: 비싸서 빠지는 키워드가 얼마부터 되는지 보여 주려고)
        long probeTop = Math.min(policy.maxBid(), Math.max(maxCpc * 3, profit > 0 ? Math.round(profit * cvr) : 0));
        List<Long> ladder = LADDER.stream().filter(b -> b <= probeTop).toList();
        List<PerformanceEstimate.Query> queries = new ArrayList<>();
        candidates.keySet().forEach(k -> ladder.forEach(b -> queries.add(new PerformanceEstimate.Query(k, b))));
        Map<String, List<PerformanceEstimate>> curves = new HashMap<>();
        if (!queries.isEmpty()) {
            for (PerformanceEstimate p : client.estimatePerformance(queries, "BOTH")) {
                curves.computeIfAbsent(p.keyword().replace(" ", ""), x -> new ArrayList<>()).add(p);
            }
        }

        // 3) 예산 안 추천
        List<KeywordSelector.Pick> picks = KeywordSelector.select(candidates, curves, rules, budget);

        // 4) 이미 등록된 키워드 표시
        Map<String, AdKeyword> registered = new HashMap<>();
        String adgroupName = null;
        if (req.nccAdgroupId() != null && !req.nccAdgroupId().isBlank()) {
            keywordRepository.findByAdAccountId(scope.id()).stream()
                    .filter(k -> req.nccAdgroupId().equals(k.getNccAdgroupId()))
                    .forEach(k -> registered.put(normalize(k.getKeyword()), k));
            adgroupName = adGroupRepository.findByAdAccountIdAndNccAdgroupId(scope.id(), req.nccAdgroupId())
                    .map(AdGroup::getName).orElse(null);
        }

        List<Item> items = new ArrayList<>();
        for (KeywordSelector.Pick p : picks) {
            List<PerformanceEstimate> all = curves.getOrDefault(p.keyword(), List.of());
            List<Option> options = KeywordSelector.usableCurve(all, budget).stream()
                    .map(e -> new Option(e.bid(), e.clicks(), e.cost())).toList();
            PerformanceEstimate above = all.stream()
                    .filter(e -> e.bid() > budget.maxCpc() && e.clicks() > 0)
                    .min(Comparator.comparingLong(PerformanceEstimate::bid)).orElse(null);
            AdKeyword reg = registered.get(normalize(p.keyword()));
            items.add(new Item(p.keyword(), p.monthlySearches(), round1(p.ctrPct()), p.competition(), p.weight(),
                    p.tag(), p.bid(), p.clicks(), p.cost(), p.reason(), reg != null,
                    reg != null ? reg.getBidAmt() : null, options,
                    above != null ? above.bid() : 0, above != null ? above.clicks() : 0,
                    above != null ? above.cost() : 0));
        }

        Summary summary = summarize(items, monthlyBudget, maxCpc, cvr, profit, candidates.size());
        return new Proposal(req.nccCampaignId(), req.nccAdgroupId(), adgroupName, summary,
                insights(items, summary, cvr, profit), items);
    }

    private static Summary summarize(List<Item> items, long monthlyBudget, long maxCpc, double cvr, long profit,
            int candidates) {
        long clicks = 0;
        long cost = 0;
        double conversions = 0;
        int picked = 0;
        for (Item i : items) {
            if (i.recommendedBid() > 0) {
                picked++;
                clicks += i.clicks();
                cost += i.cost();
                conversions += i.clicks() * cvr * i.weight();
            }
        }
        double conv = Math.round(conversions * 10) / 10.0;
        return new Summary(candidates, picked, clicks, cost, clicks > 0 ? cost / clicks : 0, conv,
                profit > 0 ? Math.round(conversions * profit) - cost : 0, monthlyBudget, maxCpc,
                round1(cvr * 100), profit);
    }

    /** 초보도 읽을 수 있는 전체 해설. 숫자는 모두 위 표에서 나온 것이다. */
    static List<String> insights(List<Item> items, Summary s, double cvr, long profit) {
        List<String> out = new ArrayList<>();
        if (s.picked() == 0) {
            out.add("지금 기준(클릭 1회 최대 " + won(s.maxCpc()) + ")으로는 예산 안에서 넣을 키워드가 없습니다. "
                    + "최대 금액을 올리거나 힌트를 바꿔 보세요.");
        } else {
            out.add(String.format("후보 %d개 중 %d개를 추천합니다. 한 달에 약 %,d번 클릭되고 광고비 %s(클릭당 평균 %s)가 들 것으로 보입니다.",
                    s.candidates(), s.picked(), s.clicks(), won(s.cost()), won(s.avgCpc())));
            if (profit > 0) {
                out.add(String.format("전환율 %.1f%%(고객층·상품 말이 든 키워드는 가중)로 보면 문의·계약 약 %.1f건, "
                                + "건당 %s 남는다면 광고비를 빼고 약 %s 남습니다.",
                        s.cvrPct(), s.conversions(), won(profit), won(s.profit())));
            }
            long usedPct = s.monthlyBudget() > 0 ? Math.round(s.cost() * 100.0 / s.monthlyBudget()) : 0;
            if (usedPct < 70) {
                out.add(String.format("한 달 예산 %s 중 %d%%만 씁니다. 이 상품 검색이 많지 않거나, 더 쓰려면 클릭당 최대 금액을 올려야 합니다.",
                        won(s.monthlyBudget()), usedPct));
            }
        }
        List<Item> strongButExpensive = items.stream()
                .filter(i -> KeywordSelector.Pick.OVER_CAP.equals(i.tag()) && i.minBidAboveCap() > 0)
                .sorted(Comparator.comparingDouble((Item i) -> i.ctrPct() * i.monthlySearches()).reversed())
                .limit(5).toList();
        if (!strongButExpensive.isEmpty()) {
            StringBuilder sb = new StringBuilder("클릭 1회 최대 " + won(s.maxCpc()) + "로는 못 들어간 키워드: ");
            for (Item i : strongButExpensive) {
                sb.append(String.format("%s(월 검색 %,d·클릭률 %.1f%% → %s부터 월 %,d클릭), ", i.keyword(),
                        i.monthlySearches(), i.ctrPct(), won(i.minBidAboveCap()), i.clicksAboveCap()));
            }
            sb.setLength(sb.length() - 2);
            long breakEvenCpc = profit > 0 ? Math.round(profit * cvr) : 0;
            sb.append(breakEvenCpc > 0
                    ? String.format(". 건당 남는 돈 기준으로는 클릭당 %s까지 남는 장사라, 최대 금액을 올리면 넣을 수 있습니다.", won(breakEvenCpc))
                    : ". 최대 금액을 올리면 넣을 수 있습니다.");
            out.add(sb.toString());
        }
        List<String> weak = items.stream()
                .filter(i -> i.recommendedBid() > 0 && i.weight() <= 1.0)
                .map(Item::keyword).limit(6).toList();
        if (!weak.isEmpty()) {
            out.add("상품 말·고객층 말이 없는 키워드(" + String.join(", ", weak) + ")는 싸서 추천에 들었지만 "
                    + "다른 목적의 검색일 수 있습니다. 빼고 싶으면 체크를 풀어 주세요.");
        }
        List<String> best = items.stream()
                .filter(i -> i.recommendedBid() > 0 && i.clicks() > 0)
                .sorted(Comparator.comparingDouble((Item i) -> i.clicks() * i.weight() / (double) Math.max(1, i.cost())).reversed())
                .limit(3).map(i -> String.format("%s(%s에 월 %,d클릭)", i.keyword(), won(i.recommendedBid()), i.clicks()))
                .toList();
        if (!best.isEmpty()) {
            out.add("광고비 대비 효율이 가장 좋은 키워드: " + String.join(", ", best) + ".");
        }
        return out;
    }

    // --- 적용 ---

    /** 사용자가 고른 키워드·입찰가만 네이버에 반영한다. 서버가 기록만 하는 상태면 기록만 남긴다. */
    public RunResult apply(Long accountId, ApplyRequest req) {
        AccountScope scope = accountService.scope(accountId);
        if (req.nccAdgroupId() == null || req.nccAdgroupId().isBlank()) {
            throw new BadRequestException("키워드를 넣을 광고그룹을 골라 주세요.");
        }
        adGroupRepository.findByAdAccountIdAndNccAdgroupId(scope.id(), req.nccAdgroupId())
                .orElseThrow(() -> new BadRequestException("이 계정의 광고그룹이 아닙니다."));
        return applyTo(scope, req, RUN_PREFIX);
    }

    RunResult applyTo(AccountScope scope, ApplyRequest req, String prefix) {
        SearchAdClient client = scope.client();
        boolean dry = autopilotService.recordOnly(scope.id());
        String idle = dry ? AutopilotService.STATUS_DRY_RUN : AutopilotService.STATUS_APPLIED;
        List<Action> actions = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String adgroupId = req.nccAdgroupId();

        Map<String, NccKeyword> existing = new HashMap<>();
        client.listKeywords(adgroupId).forEach(k -> existing.put(normalize(k.keyword()), k));
        Set<String> keep = new HashSet<>();
        for (ApplyItem item : req.items() != null ? req.items() : List.<ApplyItem>of()) {
            String keyword = item.keyword() == null ? "" : item.keyword().replace(" ", "").trim();
            if (keyword.isEmpty() || !keep.add(normalize(keyword))) {
                continue;
            }
            long bid = Math.max(policy.minBid(), Math.min(policy.maxBid(), roundTo10(item.bid())));
            NccKeyword now = existing.get(normalize(keyword));
            if (now != null && now.bidAmt() != null && now.bidAmt() == bid && !Boolean.TRUE.equals(now.useGroupBidAmt())) {
                continue;
            }
            String status = idle;
            if (!dry) {
                try {
                    if (now == null) {
                        List<NccKeyword> created = client.createKeywords(adgroupId,
                                List.of(new SearchAdClient.NewKeyword(keyword, bid)));
                        tx.executeWithoutResult(s -> created.stream()
                                .filter(k -> k.nccKeywordId() != null && !k.nccKeywordId().isBlank())
                                .forEach(k -> keywordRepository.findByAdAccountIdAndNccKeywordId(scope.id(), k.nccKeywordId())
                                        .ifPresentOrElse(e -> e.update(k.keyword(), k.status(), k.bidAmt(), k.useGroupBidAmt()),
                                                () -> keywordRepository.save(new AdKeyword(scope.id(), k.nccKeywordId(),
                                                        k.nccAdgroupId(), k.keyword(), k.status(), k.bidAmt(),
                                                        k.useGroupBidAmt())))));
                    } else {
                        long applied = client.updateKeywordBid(now.nccKeywordId(), adgroupId, now.keyword(), bid);
                        tx.executeWithoutResult(s -> keywordRepository
                                .findByAdAccountIdAndNccKeywordId(scope.id(), now.nccKeywordId())
                                .ifPresent(e -> e.update(e.getKeyword(), e.getStatus(), applied, false)));
                    }
                } catch (RuntimeException e) {
                    log.error("키워드 제안 반영 실패: {}", keyword, e);
                    status = AutopilotService.STATUS_FAILED;
                }
            }
            actions.add(new Action(now == null ? "키워드 추가" : "입찰가 변경", keyword,
                    now == null ? "없음" : won(now.bidAmt() != null ? now.bidAmt() : 0), won(bid),
                    "키워드 분석·제안에서 고른 입찰가입니다.", status));
        }
        if (req.removeUnselected()) {
            for (NccKeyword k : existing.values()) {
                if (keep.contains(normalize(k.keyword()))) {
                    continue;
                }
                String status = idle;
                if (!dry) {
                    try {
                        client.deleteKeyword(k.nccKeywordId());
                        tx.executeWithoutResult(s -> keywordRepository
                                .findByAdAccountIdAndNccKeywordId(scope.id(), k.nccKeywordId())
                                .ifPresent(keywordRepository::delete));
                    } catch (RuntimeException e) {
                        log.error("고르지 않은 키워드 삭제 실패: {}", k.keyword(), e);
                        status = AutopilotService.STATUS_FAILED;
                    }
                }
                actions.add(new Action("키워드 삭제", k.keyword(), won(k.bidAmt() != null ? k.bidAmt() : 0), "삭제",
                        "키워드 분석·제안에서 고르지 않아 지웁니다.", status));
            }
        }
        if (dry) {
            notes.add("서버가 기록만 하는 상태(시험 운영·지켜보기)라 네이버에는 반영하지 않았습니다.");
        }
        return finish(scope.id(), dry, prefix, actions, notes);
    }

    private RunResult finish(Long accountId, boolean dry, String prefix, List<Action> actions, List<String> notes) {
        String summary = prefix + " " + AutopilotService.summary(actions, false, dry);
        RunResult draft = new RunResult(null, false, dry, summary, actions, notes, LocalDateTime.now());
        if (actions.isEmpty()) {
            return draft;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            json = "{}";
        }
        String content = json;
        AutopilotRun saved = tx.execute(s -> runRepository.save(
                new AutopilotRun(accountId, LocalDate.now(), false, dry, summary, content)));
        log.info("키워드 제안 반영: 계정 {} {}", accountId, summary);
        return new RunResult(saved.getId(), false, dry, summary, actions, notes, draft.ranAt());
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank() && !out.contains(v.trim())) {
                out.add(v.trim());
            }
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static long roundTo10(long value) {
        return Math.round(value / 10.0) * 10;
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    static String normalize(String keyword) {
        return keyword == null ? "" : keyword.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
