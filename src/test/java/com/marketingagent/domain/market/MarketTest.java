package com.marketingagent.domain.market;

import com.marketingagent.support.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.market.MarketDtos.CompetitorReport;
import com.marketingagent.domain.market.MarketDtos.MarketKeyword;
import com.marketingagent.domain.market.MarketDtos.MarketOverview;
import com.marketingagent.domain.onboarding.OnboardingDtos.ProfileRequest;
import com.marketingagent.domain.onboarding.OnboardingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Stub 데이터로 시장·경쟁 화면의 계산을 확인한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:market;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
class MarketTest {

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;
    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private AiProvider aiProvider;

    @Test
    void showsDemandAndCompetitionForTheMembersOwnKeywords() {
        accounts.runAs(8401L, acc -> {
            // 아무 키워드도 없으면 무엇을 살펴볼지 모른다.
            assertThatThrownBy(() -> marketService.overview(acc, null)).isInstanceOf(BadRequestException.class);

            MarketOverview typed = marketService.overview(acc, "꽃배달, 화환");
            assertThat(typed.seeds()).containsExactly("꽃배달", "화환");
            assertThat(typed.seedSource()).isEqualTo("직접 입력");
            assertThat(typed.keywords()).isNotEmpty().allSatisfy(k -> {
                assertThat(k.totalSearches()).isEqualTo(k.pcSearches() + k.mobileSearches());
                assertThat(k.relevant()).isTrue();
                // 순위가 높을수록 입찰가가 비싸다.
                assertThat(k.bidRank1()).isGreaterThanOrEqualTo(k.bidRank3());
                assertThat(k.bidRank3()).isGreaterThanOrEqualTo(k.bidRank5());
                assertThat(k.crowdRatio()).isGreaterThanOrEqualTo(1.0);
            });
            assertThat(typed.totalSearches())
                    .isEqualTo(typed.keywords().stream().mapToLong(MarketKeyword::totalSearches).sum());
            assertThat(typed.insights()).isNotEmpty();
            // 기회 키워드는 경쟁이 높지 않은 것만.
            assertThat(typed.keywords()).filteredOn(MarketKeyword::opportunity)
                    .allSatisfy(k -> assertThat(k.competition()).isNotEqualTo("높음"));

            // 업체 정보를 등록하면 그 키워드가 기본이 된다.
            onboardingService.submit(acc, new ProfileRequest("꽃집", "http://127.0.0.1/", "꽃다발과 화환을 배달합니다.",
                    List.of("꽃다발"), 200_000, 5, null, null, null));
            MarketOverview fromProfile = marketService.overview(acc, "");
            assertThat(fromProfile.seeds()).containsExactly("꽃다발");
            assertThat(fromProfile.seedSource()).contains("업체 정보");
        });
    }

    @Test
    void comparesCompetitorSitesWithoutInventingTheirAdSpend() {
        accounts.runAs(8402L, acc -> {
            onboardingService.submit(acc, new ProfileRequest("꽃집", "http://127.0.0.1/", "꽃다발과 화환을 배달합니다.",
                    List.of("꽃다발"), 200_000, 5, null, null, null));

            // 내부 주소는 열지 않으므로 모두 '열어 보지 못함'으로 나온다. 바깥 망 없이 흐름만 확인한다.
            CompetitorReport report = marketService.competitors(acc, List.of("http://10.0.0.9/", "http://10.0.0.9/"));

            assertThat(report.sites()).hasSize(2);
            assertThat(report.sites().get(0).mine()).isTrue();
            assertThat(report.sites().get(1).url()).isEqualTo("http://10.0.0.9/");
            assertThat(report.sites()).allSatisfy(s -> assertThat(s.check().reachable()).isFalse());
            assertThat(report.keywordCompetition()).isNotEmpty();
            assertThat(report.keywordCompetition().get(0).bidRank1())
                    .isGreaterThanOrEqualTo(report.keywordCompetition().get(report.keywordCompetition().size() - 1).bidRank1());
            assertThat(report.limits()).anySatisfy(l -> assertThat(l).contains("공개하지 않습니다"));

            assertThatThrownBy(() -> marketService.competitors(acc, List.of(" "))).isInstanceOf(BadRequestException.class);
        });
    }

    @Test
    void sampleContentDoesNotAssumeAnIndustry() {
        String text = aiProvider.generate(new AiRequest("",
                "주제: 꽃다발 고르는 법\n브랜드: 동네꽃집\n콘텐츠 유형: BLOG\n", null)).content();
        assertThat(text).contains("견본", "꽃다발 고르는 법", "동네꽃집");
        assertThat(text).doesNotContain("대출", "금리", "담보", "부동산");
    }
}
