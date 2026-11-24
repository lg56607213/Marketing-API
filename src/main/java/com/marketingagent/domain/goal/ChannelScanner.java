package com.marketingagent.domain.goal;

import com.marketingagent.domain.goal.BusinessModel.Competitor;
import com.marketingagent.domain.onboarding.SiteInspector;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 경쟁사·업계 선도 업체가 어떤 광고 채널을 쓰는지 공개된 흔적으로 추정하고, 내가 안 쓰는 채널을 근거와 함께 권한다.
 *
 * <p>근거로 쓰는 것은 누구나 볼 수 있는 것뿐이다.
 * <ul>
 *   <li>홈페이지 첫 화면 소스에 심어 둔 광고 추적 코드(픽셀). 메타 픽셀이 있으면 메타 광고 성과를 재고 있다는 뜻이라
 *       메타 광고를 집행 중일 가능성이 높다.</li>
 *   <li>홈페이지에 걸린 채널 링크(블로그·플레이스·인스타그램·유튜브·카카오톡 채널 등).</li>
 *   <li>네이버 키워드도구의 업체 이름 월 검색수. 검색광고 밖에서 이름을 알린 정도를 가늠하는 지표다.</li>
 * </ul>
 * 경쟁사의 실제 광고비·성과는 어디에도 공개되지 않으므로 숫자로 말하지 않고 "추정"이라고 적는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelScanner {

    static final int MAX_SITES = 4;

    private final SiteInspector siteInspector;

    /** 광고(돈을 내고 사는) 채널인지, 직접 운영하는 채널인지 */
    public enum Kind { PAID, OWNED }

    /**
     * 홈페이지 소스에서 찾을 흔적과 그 채널에 대한 설명.
     *
     * @param effect 이 채널을 쓰는 업체가 보통 얻으려는 효과. 일반적인 쓰임새이며 그 업체의 실제 성과가 아니다
     */
    record Signature(String channel, Kind kind, List<String> needles, String evidence, String effect) {}

    static final List<Signature> SIGNATURES = List.of(
            new Signature("네이버 검색광고 전환 추적", Kind.PAID, List.of("wcs.naver.net", "wcslog.js", "wcs_add"),
                    "네이버 프리미엄 로그분석(wcslog) 스크립트",
                    "검색광고 키워드별로 문의·구매를 재서 입찰가를 조정한다"),
            new Signature("네이버 성과형 디스플레이(GFA)", Kind.PAID, List.of("ntm.pstatic.net/scripts/ntm_", "nlog.naver.com"),
                    "네이버 태그 매니저·GFA 추적 코드",
                    "네이버 메인·밴드·웹툰 등 지면에 이미지 광고를 띄워 검색 전 단계 고객과 재방문 고객을 모은다"),
            new Signature("메타(페이스북·인스타그램) 광고", Kind.PAID, List.of("connect.facebook.net", "fbq("),
                    "메타 픽셀(fbq)",
                    "관심사·유사 고객 타깃과 홈페이지 방문자 리타게팅으로, 검색하기 전의 잠재 고객에게 먼저 다가간다"),
            new Signature("카카오모먼트 광고", Kind.PAID, List.of("t1.daumcdn.net/kas/static/kp.js", "kakaopixel("),
                    "카카오 픽셀",
                    "카카오톡·다음 지면 광고와 방문자 리타게팅, 카카오톡 채널 친구 모으기에 쓴다"),
            new Signature("구글 광고(검색·디스플레이·유튜브)", Kind.PAID,
                    List.of("googleadservices.com", "googleads.g.doubleclick.net", "'aw-", "\"aw-", "/aw-"),
                    "구글 Ads 전환 태그(AW-)",
                    "구글 검색·유튜브·디스플레이 네트워크에서 광고하고 전환을 잰다"),
            new Signature("틱톡 광고", Kind.PAID, List.of("analytics.tiktok.com"),
                    "틱톡 픽셀",
                    "짧은 영상으로 젊은 층에 노출하고 홈페이지 방문을 유도한다"),
            new Signature("크리테오 리타게팅", Kind.PAID, List.of("static.criteo.net", "criteo.com/js"),
                    "크리테오 태그",
                    "홈페이지에 왔다 간 사람에게 다른 사이트에서 다시 광고를 보여 준다"),
            new Signature("모비온·데이블 등 네이티브/리타게팅", Kind.PAID, List.of("mobon.net", "dable.io", "adop.cc"),
                    "국내 리타게팅·네이티브 광고 태그",
                    "언론사·커뮤니티 지면에서 기사형 광고나 리타게팅 광고를 보여 준다"),
            new Signature("네이버 블로그", Kind.OWNED, List.of("blog.naver.com/"),
                    "홈페이지에 네이버 블로그 링크",
                    "후기·정보성 글로 검색 결과의 블로그 영역에 노출된다(검색광고보다 싸게 신뢰를 쌓는다)"),
            new Signature("네이버 플레이스", Kind.OWNED, List.of("place.naver.com", "map.naver.com", "naver.me/"),
                    "홈페이지에 네이버 플레이스·지도 링크",
                    "지역 검색과 지도에서 업체가 노출되고 리뷰가 쌓인다"),
            new Signature("네이버 톡톡", Kind.OWNED, List.of("talk.naver.com"),
                    "네이버 톡톡 상담 링크",
                    "전화가 부담스러운 고객의 문의를 채팅으로 받는다(문의 전환율을 올린다)"),
            new Signature("카카오톡 채널", Kind.OWNED, List.of("pf.kakao.com"),
                    "카카오톡 채널 링크",
                    "카카오톡으로 상담을 받고 친구에게 다시 소식을 보낸다"),
            new Signature("인스타그램", Kind.OWNED, List.of("instagram.com/"),
                    "인스타그램 계정 링크",
                    "사진·릴스로 브랜드를 보여 주고 광고 소재를 시험한다"),
            new Signature("유튜브", Kind.OWNED, List.of("youtube.com/@", "youtube.com/channel", "youtube.com/c/"),
                    "유튜브 채널 링크",
                    "설명이 필요한 상품을 영상으로 풀어 신뢰를 쌓는다"),
            new Signature("네이버 스마트스토어", Kind.OWNED, List.of("smartstore.naver.com"),
                    "스마트스토어 링크",
                    "네이버 쇼핑 검색과 쇼핑검색광고에 상품을 올린다")
    );

    public record ChannelSignal(String channel, Kind kind, String evidence) {}

    /**
     * 업체 한 곳을 살펴본 결과.
     *
     * @param source         USER(사장님이 알려 줌) · AI(AI 가 업계 선도 업체로 추정) · MINE(내 홈페이지)
     * @param brandSearches  업체 이름의 네이버 월 검색수. 모르면 null
     */
    public record SiteScan(
            String name,
            String url,
            String source,
            boolean reachable,
            String title,
            List<ChannelSignal> channels,
            Long brandSearches,
            String note
    ) {}

    /**
     * 채널 하나에 대한 권유. evidence 는 "어느 업체가 어떤 흔적으로 이 채널을 쓰는 것으로 보이는지" 한 줄씩.
     *
     * @param confidence 추정 · 일반 관행 — 숫자 근거가 없는 정성 제안임을 화면에 그대로 보여 준다
     */
    public record ChannelSuggestion(
            String channel,
            Kind kind,
            String effect,
            List<String> evidence,
            int adopters,
            String confidence,
            String suggestion
    ) {}

    public record Result(List<SiteScan> sites, List<ChannelSuggestion> suggestions, List<String> limits) {}

    /**
     * @param mine        내 홈페이지 주소들(없으면 비움). 내가 이미 쓰는 채널은 권하지 않는다
     * @param competitors 살펴볼 경쟁사·선도 업체
     * @param client      네이버 키워드도구로 업체 이름 검색수를 볼 때 쓴다. null 이면 건너뛴다
     */
    public Result scan(List<String> mine, List<Competitor> competitors, SearchAdClient client) {
        List<Competitor> targets = new ArrayList<>();
        mine.stream().distinct().limit(2).forEach(url -> targets.add(new Competitor(null, url, "MINE")));
        competitors.stream().filter(c -> c.getUrl() != null && !c.getUrl().isBlank())
                .limit(MAX_SITES).forEach(targets::add);

        // 홈페이지는 한 곳에 최대 8초가 걸릴 수 있어 동시에 연다.
        List<CompletableFuture<Optional<String>>> pages = targets.stream()
                .map(t -> CompletableFuture.supplyAsync(() -> siteInspector.html(t.getUrl())))
                .toList();

        Map<String, Long> brand = brandSearches(competitors, client);
        List<SiteScan> sites = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Competitor t = targets.get(i);
            Optional<String> html = pages.get(i).join();
            String name = t.getName() == null || t.getName().isBlank() ? null : t.getName();
            if (html.isEmpty()) {
                sites.add(new SiteScan(name, t.getUrl(), t.getSource(), false, null, List.of(), brand.get(name),
                        "AI".equals(t.getSource())
                                ? "AI 가 추정한 주소가 열리지 않았습니다. 실제 주소를 알려 주시면 다시 살펴봅니다."
                                : "홈페이지를 열지 못했습니다."));
                continue;
            }
            sites.add(new SiteScan(name, t.getUrl(), t.getSource(), true, title(html.get()), detect(html.get()),
                    brand.get(name), null));
        }
        return new Result(sites, suggest(sites), List.of(
                "경쟁사의 실제 광고비·성과는 공개되지 않습니다. 홈페이지에 심어 둔 추적 코드와 채널 링크로 '쓰고 있을 가능성'을 추정한 것입니다.",
                "추적 코드가 있어도 지금은 광고를 멈췄을 수 있고, 첫 화면 소스에 없으면 쓰고 있어도 안 보일 수 있습니다.",
                "업체 이름 검색수는 그 업체가 검색광고 밖에서 이름을 알린 정도를 가늠하는 참고값입니다."));
    }

    /** 홈페이지 소스에서 채널 흔적을 찾는다. */
    static List<ChannelSignal> detect(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        List<ChannelSignal> found = new ArrayList<>();
        for (Signature s : SIGNATURES) {
            if (s.needles().stream().anyMatch(lower::contains)) {
                found.add(new ChannelSignal(s.channel(), s.kind(), s.evidence()));
            }
        }
        return found;
    }

    /** 경쟁사가 쓰는데 나는 안 쓰는 채널을, 쓰는 업체가 많은 순으로 권한다. */
    static List<ChannelSuggestion> suggest(List<SiteScan> sites) {
        Set<String> mineUses = new LinkedHashSet<>();
        sites.stream().filter(s -> "MINE".equals(s.source()) && s.reachable())
                .forEach(s -> s.channels().forEach(c -> mineUses.add(c.channel())));
        boolean mineKnown = sites.stream().anyMatch(s -> "MINE".equals(s.source()) && s.reachable());

        Map<String, List<String>> evidence = new LinkedHashMap<>();
        for (SiteScan s : sites) {
            if ("MINE".equals(s.source()) || !s.reachable()) {
                continue;
            }
            String who = label(s);
            for (ChannelSignal c : s.channels()) {
                String line = String.format("%s 홈페이지에 %s이(가) 있어 %s을(를) 쓰는 것으로 추정됩니다%s.", who, c.evidence(),
                        c.channel(), s.brandSearches() != null && s.brandSearches() > 0
                                ? String.format(" (업체 이름 월 검색 %,d회)", s.brandSearches()) : "");
                evidence.computeIfAbsent(c.channel(), k -> new ArrayList<>()).add(line);
            }
        }

        Map<String, Signature> byChannel = new LinkedHashMap<>();
        SIGNATURES.forEach(s -> byChannel.put(s.channel(), s));
        List<ChannelSuggestion> result = new ArrayList<>();
        evidence.forEach((channel, lines) -> {
            if (mineUses.contains(channel)) {
                return;
            }
            Signature sig = byChannel.get(channel);
            String suggestion = String.format("경쟁 업체 %d곳이 %s을(를) 쓰는 것으로 보입니다. 이 채널은 보통 %s. %s",
                    lines.size(), channel, sig.effect(),
                    mineKnown ? "내 홈페이지에서는 흔적이 보이지 않으니 작은 예산으로 시험해 볼 만합니다."
                            : "내 홈페이지 주소를 알려 주시면 이미 쓰고 있는지도 확인해 드립니다.");
            result.add(new ChannelSuggestion(channel, sig.kind(), sig.effect(), lines, lines.size(), "추정", suggestion));
        });
        result.sort((a, b) -> b.adopters() != a.adopters() ? b.adopters() - a.adopters()
                : a.kind().compareTo(b.kind()));
        return result;
    }

    private static String label(SiteScan s) {
        String name = s.name() != null ? s.name() : s.title() != null ? s.title() : s.url();
        return ("AI".equals(s.source()) ? "업계 선도 업체로 보이는 " : "경쟁 업체 ") + "'" + name + "'";
    }

    private Map<String, Long> brandSearches(List<Competitor> competitors, SearchAdClient client) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (client == null) {
            return result;
        }
        List<String> names = competitors.stream().map(Competitor::getName)
                .filter(n -> n != null && !n.isBlank()).distinct().limit(5).toList();
        if (names.isEmpty()) {
            return result;
        }
        try {
            Map<String, RelatedKeyword> found = new LinkedHashMap<>();
            client.relatedKeywords(names).forEach(k -> found.putIfAbsent(KeywordBidResearch.norm(k.keyword()), k));
            Function<String, Optional<RelatedKeyword>> lookup = n -> Optional.ofNullable(found.get(KeywordBidResearch.norm(n)));
            names.forEach(n -> lookup.apply(n).ifPresent(k -> result.put(n, k.totalCount())));
        } catch (RuntimeException e) {
            log.info("업체 이름 검색수 조회 실패: {}", e.getMessage());
        }
        return result;
    }

    private static String title(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<title[^>]*>(.*?)</title>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(html);
        if (!m.find()) {
            return null;
        }
        String t = m.group(1).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t.length() > 80 ? t.substring(0, 80) : t;
    }
}
