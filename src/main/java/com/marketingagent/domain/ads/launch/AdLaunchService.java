package com.marketingagent.domain.ads.launch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdSyncService;
import com.marketingagent.domain.ads.BidPolicyProperties;
import com.marketingagent.domain.ads.account.AccountScope;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Action;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.autopilot.AutopilotRun;
import com.marketingagent.domain.ads.autopilot.AutopilotRunRepository;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.SearchAdClient.BusinessChannel;
import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계획 파일대로 새 광고(광고그룹 · 소재 · 키워드)를 네이버에 만들고, 캠페인의 손익 기준을 저장한다.
 *
 * <p>여러 번 돌려도 같은 결과가 되게 만든다. 같은 이름의 광고그룹이 있으면 새로 만들지 않고, 소재가 있으면 소재를 더 넣지 않고,
 * 이미 있는 키워드는 다시 넣지 않는다. 서버가 기록만 하는 상태(dry-run·지켜보기)면 네이버에는 아무것도 바꾸지 않는다.
 * 만든 뒤에는 동기화해서 자동 운영(실시간 입찰·키워드 관리)이 바로 이 그룹을 함께 맡게 한다.
 */
@Slf4j
@Service
public class AdLaunchService {

    static final String TYPE_ADGROUP = "광고그룹 생성";
    static final String TYPE_AD = "소재 생성";
    /** 자동 운영의 키워드 추가와 같은 종류로 남겨, 30일 넘게 클릭이 없으면 자동 정리 대상이 되게 한다. */
    static final String TYPE_KEYWORD = "키워드 추가";
    private static final int TARGET_RANK = 3;
    private static final int HEADLINE_MAX = 15;
    private static final int DESCRIPTION_MAX = 45;

    private final AdAccountService accountService;
    private final AdSyncService syncService;
    private final AdCampaignRepository campaignRepository;
    private final AutopilotRunRepository runRepository;
    private final AutopilotService autopilotService;
    private final BidPolicyProperties policy;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public AdLaunchService(AdAccountService accountService, AdSyncService syncService,
            AdCampaignRepository campaignRepository, AutopilotRunRepository runRepository,
            AutopilotService autopilotService, BidPolicyProperties policy, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.accountService = accountService;
        this.syncService = syncService;
        this.campaignRepository = campaignRepository;
        this.runRepository = runRepository;
        this.autopilotService = autopilotService;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 호출부가 {@code SystemTask} 범위를 열어야 한다. */
    public RunResult launch(LaunchPlan plan) {
        AccountScope scope = accountService.scope(plan.accountId());
        SearchAdClient client = scope.client();
        boolean dry = autopilotService.recordOnly(scope.id());
        String idle = dry ? AutopilotService.STATUS_DRY_RUN : AutopilotService.STATUS_APPLIED;
        List<Action> actions = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        NccCampaign campaign = client.listCampaigns().stream()
                .filter(c -> c.name() != null && c.name().trim().equalsIgnoreCase(plan.campaignName().trim()))
                .findFirst().orElse(null);
        if (campaign == null) {
            notes.add("네이버에 '" + plan.campaignName() + "' 캠페인이 없어 광고를 만들지 않았습니다.");
            return finish(scope.id(), dry, actions, notes);
        }

        // 1) 광고그룹: 같은 이름이 있으면 그대로 쓴다
        String adgroupId = client.listAdgroups(campaign.nccCampaignId()).stream()
                .filter(g -> plan.adgroupName().equals(g.name()))
                .map(NccAdgroup::nccAdgroupId)
                .findFirst().orElse(null);
        if (adgroupId == null) {
            if (dry) {
                actions.add(new Action(TYPE_ADGROUP, plan.adgroupName(), "없음", "만들 예정",
                        "캠페인 " + campaign.name() + " / 연결 " + plan.landingUrl(), idle));
                notes.add("서버가 기록만 하는 상태라 네이버에는 만들지 않았습니다.");
                return finish(scope.id(), dry, actions, notes);
            }
            BusinessChannel channel = siteChannel(client, plan, notes);
            if (channel == null) {
                return finish(scope.id(), dry, actions, notes);
            }
            NccAdgroup created = client.createAdgroup(campaign.nccCampaignId(), plan.adgroupName(), channel.id(),
                    Math.max(policy.minBid(), plan.groupBid()));
            adgroupId = created.nccAdgroupId();
            actions.add(new Action(TYPE_ADGROUP, plan.adgroupName(), "없음", won(plan.groupBid()) + " 기본 입찰",
                    "캠페인 " + campaign.name() + "에 새 광고그룹을 만들었습니다. 사이트 " + channel.key(), idle));
        }

        // 2) 소재: 같은 제목이 없는 것만 넣는다(실패한 소재는 다음 실행에서 다시 시도된다)
        if (!dry) {
            Set<String> headlines = new HashSet<>(client.listAdHeadlines(adgroupId));
            for (LaunchPlan.TextAd ad : plan.ads() != null ? plan.ads() : List.<LaunchPlan.TextAd>of()) {
                if (headlines.contains(ad.headline())) {
                    continue;
                }
                if (ad.headline().length() > HEADLINE_MAX || ad.description().length() > DESCRIPTION_MAX) {
                    notes.add("소재 글자 수가 너무 길어 넣지 않았습니다: " + ad.headline());
                    continue;
                }
                String status = idle;
                try {
                    client.createTextAd(adgroupId, ad.headline(), ad.description(), plan.landingUrl());
                } catch (RuntimeException e) {
                    log.error("새 광고 소재 생성 실패: {}", ad.headline(), e);
                    status = AutopilotService.STATUS_FAILED;
                }
                actions.add(new Action(TYPE_AD, ad.headline(), "없음", "검수 대기", ad.description(), status));
            }
        }

        // 3) 키워드: 없는 것만, 목표 순위 추정가로 하되 최대 금액을 넘지 않게
        Set<String> existing = new HashSet<>();
        if (!dry) {
            client.listKeywords(adgroupId).forEach(k -> existing.add(normalize(k.keyword())));
        }
        List<String> missing = new ArrayList<>();
        for (String k : new LinkedHashSet<>(plan.keywords() != null ? plan.keywords() : List.<String>of())) {
            String keyword = k.replace(" ", "").trim();
            if (!keyword.isEmpty() && existing.add(normalize(keyword))) {
                missing.add(keyword);
            }
        }
        long cap = plan.maxCpc() != null && plan.maxCpc() > 0 ? plan.maxCpc() : policy.maxBid();
        Map<String, Long> estimates = Map.of();
        if (!missing.isEmpty()) {
            try {
                estimates = client.estimateBidForPosition(missing, TARGET_RANK, "MOBILE");
            } catch (RuntimeException e) {
                notes.add("순위별 예상 입찰가를 받지 못해 그룹 기본 입찰가로 넣었습니다.");
            }
        }
        for (String keyword : missing) {
            Long estimate = estimates.get(keyword);
            Long bid = estimate != null && estimate > 0
                    ? Math.max(policy.minBid(), Math.min(cap, roundTo10(estimate)))
                    : null;
            String status = idle;
            if (!dry) {
                try {
                    // 하나가 거절돼도 나머지는 들어가게 한 개씩 넣는다
                    client.createKeywords(adgroupId, List.of(new SearchAdClient.NewKeyword(keyword, bid)));
                } catch (RuntimeException e) {
                    log.error("새 광고 키워드 추가 실패: {}", keyword, e);
                    status = AutopilotService.STATUS_FAILED;
                }
            }
            actions.add(new Action(TYPE_KEYWORD, keyword, "광고그룹 " + plan.adgroupName(),
                    bid != null ? won(bid) : "그룹 입찰가",
                    estimate != null && estimate > 0
                            ? String.format("새 광고 시작 키워드. %d위 예상 입찰가 %,d원, 클릭 1회 최대 %,d원 안에서 넣습니다.",
                                    TARGET_RANK, estimate, cap)
                            : "새 광고 시작 키워드. 예상 입찰가가 없어 그룹 기본 입찰가로 넣습니다.",
                    status));
        }

        // 4) 캠페인 손익 기준 저장 + 동기화 (자동 운영이 바로 이 그룹을 맡게)
        String nccCampaignId = campaign.nccCampaignId();
        if (!dry) {
            try {
                syncService.syncAccount(scope.account(), 1);
            } catch (RuntimeException e) {
                log.error("새 광고 시작 후 동기화 실패(다음 일 배치에서 다시 받습니다)", e);
            }
        }
        tx.executeWithoutResult(s -> campaignRepository.findByAdAccountIdAndNccCampaignId(scope.id(), nccCampaignId)
                .ifPresentOrElse(c -> c.changeEconomics(plan.breakEvenCpa(), plan.maxCpc(), plan.businessNote()),
                        () -> notes.add("캠페인이 아직 DB 에 없어 손익 기준을 저장하지 못했습니다. 동기화 후 다시 실행하세요.")));
        return finish(scope.id(), dry, actions, notes);
    }

    /** 사이트 주소의 도메인이 같은 웹사이트 비즈채널. 없으면 새로 등록한다. */
    private BusinessChannel siteChannel(SearchAdClient client, LaunchPlan plan, List<String> notes) {
        String host = host(plan.siteUrl());
        for (BusinessChannel c : client.listBusinessChannels()) {
            if ("SITE".equalsIgnoreCase(c.type()) && host.equals(host(c.key()))) {
                return c;
            }
        }
        try {
            BusinessChannel created = client.createSiteChannel(host, plan.siteUrl());
            notes.add("사이트 " + host + " 를 비즈채널로 새로 등록했습니다. 네이버 검수가 끝나면 광고가 노출됩니다.");
            return created;
        } catch (RuntimeException e) {
            log.error("비즈채널 등록 실패: {}", plan.siteUrl(), e);
            notes.add("사이트 " + host + " 비즈채널이 없고 등록도 실패해 광고그룹을 만들지 못했습니다. "
                    + "네이버 광고 관리 > 정보 관리 > 비즈채널 관리에서 사이트를 등록해 주세요.");
            return null;
        }
    }

    static String host(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String withScheme = url.contains("://") ? url : "https://" + url;
            String h = URI.create(withScheme.trim()).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private RunResult finish(Long accountId, boolean dry, List<Action> actions, List<String> notes) {
        String summary = "[새 광고] " + AutopilotService.summary(actions, false, dry);
        RunResult draft = new RunResult(null, false, dry, summary, actions, notes, LocalDateTime.now());
        if (actions.isEmpty()) {
            log.info("새 광고 시작: 계정 {} 바꿀 것 없음 {}", accountId, notes);
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
        log.info("새 광고 시작: 계정 {} {} {}", accountId, summary, notes);
        return new RunResult(saved.getId(), false, dry, summary, actions, notes, draft.ranAt());
    }

    private static long roundTo10(long value) {
        return Math.round(value / 10.0) * 10;
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static String normalize(String keyword) {
        return keyword == null ? "" : keyword.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
