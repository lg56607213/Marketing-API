package com.marketingagent.domain.market;

import com.marketingagent.domain.onboarding.OnboardingDtos.SiteCheck;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class MarketDtos {

    private MarketDtos() {
    }

    /**
     * 시장에서 본 키워드 한 건. 모두 네이버가 주는 최근 한 달 수치다.
     *
     * @param monthlyClicks  이 키워드의 광고들이 받은 월평균 클릭수
     * @param competition    네이버가 매긴 경쟁 정도 (높음/중간/낮음)
     * @param bidRank1       평균 1위에 노출되려면 필요한 추정 입찰가. 추정하지 않은 키워드는 null
     * @param crowdRatio     1위 입찰가 ÷ 5위 입찰가. 클수록 상위 자리 다툼이 심하다
     * @param relevant       입력한 핵심 키워드를 포함하는지
     * @param opportunity    검색은 많은데 경쟁과 입찰가가 낮은 편인지
     */
    public record MarketKeyword(
            String keyword,
            long pcSearches,
            long mobileSearches,
            long totalSearches,
            double mobileSharePct,
            long monthlyClicks,
            String competition,
            Long bidRank1,
            Long bidRank3,
            Long bidRank5,
            Double crowdRatio,
            boolean relevant,
            boolean opportunity
    ) {}

    /**
     * @param seedSource       핵심 키워드를 어디서 가져왔는지 (직접 입력 / 업체 정보 / 등록된 광고 키워드)
     * @param totalSearches    목록 전체의 월 검색수 합
     * @param medianBidRank3   3위 추정 입찰가의 중간값
     * @param highCompetitionPct 경쟁 '높음' 인 키워드의 비율(%)
     * @param insights         수치에서 읽을 수 있는 점
     */
    public record MarketOverview(
            List<String> seeds,
            String seedSource,
            long totalSearches,
            long medianBidRank3,
            double highCompetitionPct,
            double mobileSharePct,
            List<MarketKeyword> keywords,
            List<String> insights
    ) {}

    /** @param urls 경쟁사 홈페이지 주소 1~3개 */
    public record CompetitorRequest(@NotEmpty @Size(max = 3) List<String> urls) {}

    public record SiteSummary(String url, boolean mine, SiteCheck check) {}

    /**
     * @param sites             내 홈페이지(등록돼 있으면)와 경쟁사 홈페이지를 같은 기준으로 확인한 결과
     * @param keywordCompetition 내 키워드의 자리 다툼. 입찰가가 높은 순
     * @param findings          비교해서 알 수 있는 점
     * @param limits            이 화면이 알려 줄 수 없는 것
     */
    public record CompetitorReport(
            List<SiteSummary> sites,
            List<MarketKeyword> keywordCompetition,
            List<String> findings,
            List<String> limits
    ) {}
}
