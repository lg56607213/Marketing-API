package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.goal.BusinessModel.Competitor;
import com.marketingagent.domain.onboarding.SiteInspector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 경쟁사 홈페이지의 공개 흔적으로 광고 채널을 추정하고, 내가 안 쓰는 채널만 근거와 함께 권한다. */
class ChannelScannerTest {

    static final String RIVAL_HTML = """
            <html><head><title>선도렌트 - 법인 장기렌트</title>
            <script>!function(f,b,e,v,n,t,s){};fbq('init','123');</script>
            <script src="https://connect.facebook.net/en_US/fbevents.js"></script>
            <script src="//t1.daumcdn.net/kas/static/kp.js"></script>
            <script>gtag('config', 'AW-1234567');</script>
            </head><body><a href="https://blog.naver.com/leader">블로그</a>
            <a href="https://pf.kakao.com/_abc">카톡 상담</a></body></html>
            """;

    static final String MINE_HTML = """
            <html><head><title>우리 렌트</title><script src="//wcs.naver.net/wcslog.js"></script></head>
            <body><a href="https://blog.naver.com/mine">블로그</a></body></html>
            """;

    /** 실제로 인터넷에 나가지 않고 정해 둔 소스를 돌려주는 점검기 */
    static class FakeInspector extends SiteInspector {
        private final Map<String, String> pages;

        FakeInspector(Map<String, String> pages) {
            this.pages = pages;
        }

        @Override
        public Optional<String> html(String url) {
            return Optional.ofNullable(pages.get(url));
        }
    }

    @Test
    void detectsPaidAndOwnedChannelsFromPublicTraces() {
        List<ChannelScanner.ChannelSignal> found = ChannelScanner.detect(RIVAL_HTML);
        assertThat(found).extracting(ChannelScanner.ChannelSignal::channel).containsExactlyInAnyOrder(
                "메타(페이스북·인스타그램) 광고", "카카오모먼트 광고", "구글 광고(검색·디스플레이·유튜브)", "네이버 블로그",
                "카카오톡 채널");
        assertThat(ChannelScanner.detect("<html><body>gfa 스타일 문구</body></html>")).isEmpty();
    }

    @Test
    void suggestsOnlyChannelsIDoNotUseWithEvidence() {
        ChannelScanner scanner = new ChannelScanner(new FakeInspector(Map.of(
                "https://mine.example.com", MINE_HTML,
                "https://leader.example.com", RIVAL_HTML)));

        ChannelScanner.Result r = scanner.scan(List.of("https://mine.example.com"), List.of(
                new Competitor("선도렌트", "https://leader.example.com", "AI"),
                new Competitor("없는회사", "https://gone.example.com", "AI")), null);

        assertThat(r.sites()).hasSize(3);
        assertThat(r.sites().get(2).reachable()).isFalse();
        assertThat(r.sites().get(2).note()).contains("AI 가 추정한 주소");
        // 나도 쓰는 블로그는 권하지 않는다.
        assertThat(r.suggestions()).extracting(ChannelScanner.ChannelSuggestion::channel)
                .contains("메타(페이스북·인스타그램) 광고", "카카오모먼트 광고")
                .doesNotContain("네이버 블로그");
        ChannelScanner.ChannelSuggestion meta = r.suggestions().stream()
                .filter(s -> s.channel().startsWith("메타")).findFirst().orElseThrow();
        assertThat(meta.confidence()).isEqualTo("추정");
        assertThat(meta.evidence().get(0)).contains("업계 선도 업체로 보이는 '선도렌트'").contains("메타 픽셀(fbq)");
        assertThat(meta.suggestion()).contains("시험해 볼 만합니다");
        assertThat(r.limits()).isNotEmpty();
    }
}
