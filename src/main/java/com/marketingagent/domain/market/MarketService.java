package com.marketingagent.domain.market;

import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.account.AccountScope;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.market.MarketDtos.CompetitorReport;
import com.marketingagent.domain.market.MarketDtos.MarketKeyword;
import com.marketingagent.domain.market.MarketDtos.MarketOverview;
import com.marketingagent.domain.market.MarketDtos.SiteSummary;
import com.marketingagent.domain.onboarding.BusinessProfile;
import com.marketingagent.domain.onboarding.BusinessProfileRepository;
import com.marketingagent.domain.onboarding.OnboardingDtos.SiteCheck;
import com.marketingagent.domain.onboarding.SiteInspector;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 시장과 경쟁을 네이버가 실제로 주는 수치로만 보여 준다.
 *
 * <p>쓸 수 있는 것: 키워드별 월 검색수(PC/모바일), 월평균 광고 클릭수, 경쟁 정도, 순위별 추정 입찰가, 그리고 누구나 열 수 있는 홈페이지.
 * 쓸 수 없는 것: 경쟁사가 실제로 쓰는 광고비·입찰가·키워드 목록. 네이버가 공개하지 않으므로 추측해서 채우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private static final int MAX_KEYWORDS = 30;
    private static final int MAX_BID_KEYWORDS = 20;
    private static final long MIN_SEARCHES = 30;
    private static final String DEVICE = "MOBILE";

    private final AdAccountService accountService;
    private final BusinessProfileRepository profileRepository;
    private final AdKeywordRepository keywordRepository;
    private final SiteInspector siteInspector;

    /**
     * @param seedsInput 쉼표로 구분한 핵심 키워드. 비우면 업체 정보의 키워드, 그것도 없으면 등록된 광고 키워드를 쓴다
     */
    public MarketOverview overview(Long accountId, String seedsInput) {
        AccountScope scope = accountService.scope(accountId);
        Seeds seeds = resolveSeeds(scope, seedsInput);
        List<String> tokens = seeds.values().stream().map(MarketService::normalize).toList();

        Map<String, RelatedKeyword> unique = new LinkedHashMap<>();
        for (RelatedKeyword keyword : scope.client().relatedKeywords(seeds.values())) {
            if (keyword.totalCount() >= MIN_SEARCHES) {
                unique.putIfAbsent(keyword.keyword(), keyword);
            }
        }
        List<RelatedKeyword> shortlist = unique.values().stream()
                .sorted(Comparator.comparing((RelatedKeyword k) -> !isRelevant(k.keyword(), tokens))
                        .thenComparing(Comparator.comparingLong(RelatedKeyword::totalCount).reversed()))
                .limit(MAX_KEYWORDS)
                .toList();
        if (shortlist.isEmpty()) {
            throw new BadRequestException("이 키워드로는 검색량이 있는 연관 키워드를 찾지 못했습니다. 더 일반적인 말로 다시 시도하세요.");
        }

        List<String> forBids = shortlist.stream().limit(MAX_BID_KEYWORDS).map(RelatedKeyword::keyword).toList();
        Map<String, Long> rank1 = bids(scope, forBids, 1);
        Map<String, Long> rank3 = bids(scope, forBids, 3);
        Map<String, Long> rank5 = bids(scope, forBids, 5);

        long medianSearches = median(shortlist.stream().map(RelatedKeyword::totalCount).toList());
        long medianBid = median(rank3.values().stream().filter(b -> b != null && b > 0).toList());

        List<MarketKeyword> keywords = new ArrayList<>();
        for (RelatedKeyword k : shortlist) {
            Long b1 = positive(rank1.get(k.keyword()));
            Long b3 = positive(rank3.get(k.keyword()));
            Long b5 = positive(rank5.get(k.keyword()));
            Double crowd = b1 != null && b5 != null ? round1(b1 / (double) b5) : null;
            boolean relevant = isRelevant(k.keyword(), tokens);
            boolean opportunity = relevant && b3 != null && k.totalCount() >= medianSearches && b3 <= medianBid
                    && !"높음".equals(k.competition());
            keywords.add(new MarketKeyword(k.keyword(), k.pcCount(), k.mobileCount(), k.totalCount(),
                    k.totalCount() == 0 ? 0.0 : round1(k.mobileCount() * 100.0 / k.totalCount()),
                    Math.round(k.pcClick() + k.mobileClick()), k.competition(), b1, b3, b5, crowd, relevant,
                    opportunity));
        }

        long total = keywords.stream().mapToLong(MarketKeyword::totalSearches).sum();
        long mobile = keywords.stream().mapToLong(MarketKeyword::mobileSearches).sum();
        long high = keywords.stream().filter(k -> "높음".equals(k.competition())).count();
        double highPct = round1(high * 100.0 / keywords.size());
        double mobilePct = total == 0 ? 0.0 : round1(mobile * 100.0 / total);

        return new MarketOverview(seeds.values(), seeds.source(), total, medianBid, highPct, mobilePct, keywords,
                insights(keywords, total, mobilePct, highPct, medianBid));
    }

    /** 내 홈페이지와 경쟁사 홈페이지를 같은 기준으로 확인하고, 내 키워드의 자리 다툼을 함께 보여 준다. */
    public CompetitorReport competitors(Long accountId, List<String> urls) {
        AccountScope scope = accountService.scope(accountId);
        List<String> targets = new ArrayList<>(new LinkedHashSet<>(
                urls.stream().filter(u -> u != null && !u.isBlank()).map(SiteInspector::normalize).toList()));
        if (targets.isEmpty()) {
            throw new BadRequestException("경쟁사 홈페이지 주소를 1개 이상 입력하세요.");
        }

        List<SiteSummary> sites = new ArrayList<>();
        String mine = profileRepository.findByAdAccountId(scope.id()).map(BusinessProfile::getHomepageUrl).orElse(null);
        if (mine != null) {
            sites.add(new SiteSummary(mine, true, siteInspector.inspect(mine)));
        }
        for (String url : targets) {
            if (!url.equalsIgnoreCase(mine)) {
                sites.add(new SiteSummary(url, false, siteInspector.inspect(url)));
            }
        }

        List<MarketKeyword> competition = List.of();
        List<String> findings = new ArrayList<>();
        try {
            competition = overview(scope.id(), null).keywords().stream()
                    .filter(k -> k.relevant() && k.bidRank1() != null)
                    .sorted(Comparator.comparingLong(MarketKeyword::bidRank1).reversed())
                    .limit(10)
                    .toList();
        } catch (BadRequestException e) {
            findings.add("키워드별 경쟁 강도는 계산하지 못했습니다. " + e.getMessage());
        }
        findings.addAll(0, siteFindings(sites));
        if (!competition.isEmpty()) {
            MarketKeyword top = competition.get(0);
            findings.add(String.format("자리 다툼이 가장 비싼 키워드는 '%s'입니다. 1위에 서려면 클릭당 약 %,d원, 5위는 약 %,d원입니다. "
                            + "상위 자리를 두고 여러 광고주가 입찰가를 올리고 있다는 뜻입니다.",
                    top.keyword(), top.bidRank1(), top.bidRank5() == null ? 0 : top.bidRank5()));
        }

        List<String> limits = List.of(
                "경쟁사가 실제로 쓰는 광고비, 입찰가, 등록한 키워드 목록은 네이버가 공개하지 않습니다. 이 화면은 그 숫자를 추측해 채우지 않습니다.",
                "홈페이지는 첫 화면의 소스만 확인합니다. 스크립트가 다른 방식으로 들어가 있으면 '없음'으로 보일 수 있습니다.",
                "경쟁사가 어떤 광고 문구를 쓰는지는 네이버에서 내 키워드를 직접 검색해 보는 것이 가장 정확합니다.");
        return new CompetitorReport(sites, competition, findings, limits);
    }

    // --- 보조 ---

    private Seeds resolveSeeds(AccountScope scope, String input) {
        List<String> typed = split(input);
        if (!typed.isEmpty()) {
            return new Seeds(typed, "직접 입력");
        }
        List<String> fromProfile = profileRepository.findByAdAccountId(scope.id())
                .map(profile -> split(profile.getKeywords()))
                .orElse(List.of());
        if (!fromProfile.isEmpty()) {
            return new Seeds(fromProfile, "업체 정보의 핵심 키워드");
        }
        List<String> registered = keywordRepository.findByAdAccountId(scope.id()).stream()
                .map(AdKeyword::getKeyword)
                .distinct()
                .limit(5)
                .toList();
        if (!registered.isEmpty()) {
            return new Seeds(registered, "등록된 광고 키워드");
        }
        throw new BadRequestException("살펴볼 키워드가 없습니다. 키워드를 입력하거나 업체 정보를 먼저 등록하세요.");
    }

    private Map<String, Long> bids(AccountScope scope, List<String> keywords, int position) {
        try {
            return scope.client().estimateBidForPosition(keywords, position, DEVICE);
        } catch (RuntimeException e) {
            log.warn("순위 {} 추정 입찰가 조회 실패: {}", position, e.getMessage());
            return Map.of();
        }
    }

    private List<String> insights(List<MarketKeyword> keywords, long total, double mobilePct, double highPct,
            long medianBid) {
        List<String> insights = new ArrayList<>();
        insights.add(String.format("이 키워드들은 한 달에 모두 약 %,d번 검색됩니다. 그중 %.0f%%가 모바일입니다.%s",
                total, mobilePct, mobilePct >= 70 ? " 광고 문구와 홈페이지를 휴대폰 화면 기준으로 맞춰야 합니다." : ""));
        if (medianBid > 0) {
            insights.add(String.format("모바일 3위에 노출되려면 클릭당 보통 %,d원 안팎이 필요합니다(중간값). 경쟁 '높음' 키워드는 전체의 %.0f%%입니다.",
                    medianBid, highPct));
        }
        List<MarketKeyword> opportunities = keywords.stream().filter(MarketKeyword::opportunity).limit(3).toList();
        if (!opportunities.isEmpty()) {
            insights.add("검색은 많은데 경쟁과 입찰가가 낮은 편인 키워드: "
                    + String.join(", ", opportunities.stream().map(MarketKeyword::keyword).toList())
                    + ". 예산이 작을 때 먼저 볼 만합니다.");
        } else {
            insights.add("검색이 많으면서 싸기까지 한 키워드는 보이지 않습니다. 검색량이 적더라도 구체적인 키워드(지역·용도가 붙은 말)를 찾아보세요.");
        }
        keywords.stream()
                .filter(k -> k.crowdRatio() != null && k.relevant())
                .max(Comparator.comparingDouble(MarketKeyword::crowdRatio))
                .filter(k -> k.crowdRatio() >= 2.0)
                .ifPresent(k -> insights.add(String.format(
                        "'%s'는 1위 입찰가가 5위의 %.1f배입니다. 1위를 고집하면 같은 클릭을 훨씬 비싸게 사게 되니 3~5위로 시작하는 편이 낫습니다.",
                        k.keyword(), k.crowdRatio())));
        return insights;
    }

    private List<String> siteFindings(List<SiteSummary> sites) {
        List<String> findings = new ArrayList<>();
        SiteSummary mine = sites.stream().filter(SiteSummary::mine).findFirst().orElse(null);
        List<SiteSummary> others = sites.stream().filter(s -> !s.mine() && s.check().reachable()).toList();
        if (mine == null) {
            findings.add("업체 정보에 내 홈페이지를 등록하면 경쟁사와 나란히 비교해 드립니다.");
        }
        long tracking = others.stream().filter(s -> s.check().naverTracking()).count();
        if (!others.isEmpty()) {
            findings.add(String.format("확인한 경쟁사 %d곳 중 %d곳에서 네이버 전환 추적이 보입니다.%s", others.size(), tracking,
                    tracking > 0 ? " 이들은 어떤 키워드가 문의로 이어지는지 재면서 광고하고 있을 가능성이 큽니다." : ""));
        }
        if (mine != null && mine.check().reachable()) {
            SiteCheck check = mine.check();
            if (!check.naverTracking() && tracking > 0) {
                findings.add("내 홈페이지에는 전환 추적이 보이지 않는데 경쟁사에는 있습니다. 같은 광고비를 써도 경쟁사는 낭비를 줄여 갈 수 있고 나는 그럴 수 없는 상태입니다.");
            }
            if (!check.hasForm() && others.stream().anyMatch(s -> s.check().hasForm())) {
                findings.add("경쟁사 첫 화면에는 문의 양식이나 전화 버튼이 있는데 내 홈페이지 첫 화면에서는 보이지 않습니다.");
            }
        }
        sites.stream().filter(s -> !s.check().reachable())
                .forEach(s -> findings.add(s.url() + " 은(는) 열어 보지 못했습니다."));
        return findings;
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,\\n]")).map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(5)
                .toList();
    }

    private static boolean isRelevant(String keyword, List<String> tokens) {
        String normalized = normalize(keyword);
        return tokens.stream().anyMatch(t -> normalized.contains(t) || t.contains(normalized));
    }

    private static String normalize(String value) {
        return value.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private static Long positive(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record Seeds(List<String> values, String source) {}
}
