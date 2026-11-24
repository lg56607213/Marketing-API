package com.marketingagent.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.KeywordStatDaily;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.SearchQueryDaily;
import com.marketingagent.domain.ads.SearchQueryDailyRepository;
import com.marketingagent.support.TestAccounts;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 저장된 실적으로 문제점·개선점·새 키워드·제외 키워드를 근거 숫자와 함께 낸다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:diagnosis;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub",
        "ai.provider=stub"
})
class PerformanceDiagnosisTest {

    @Autowired
    private PerformanceDiagnosis diagnosis;
    @Autowired
    private AdKeywordRepository keywordRepository;
    @Autowired
    private KeywordStatDailyRepository statRepository;
    @Autowired
    private SearchQueryDailyRepository searchQueryRepository;
    @Autowired
    private TestAccounts accounts;

    @Test
    void findsWasteExpensiveAndWinningKeywordsAndSearchQueryIdeas() {
        accounts.runAs(9301L, acc -> {
            LocalDate day = LocalDate.now().minusDays(3);
            keyword(acc, "k-waste", "법인장기렌트", day, 200, 200_000, 0);      // 전환 0건에 20만원
            keyword(acc, "k-win", "화물차리스", day, 300, 100_000, 10);         // 문의 1건 1만원 (목표 3만원)
            keyword(acc, "k-pricey", "장기렌트견적", day, 400, 300_000, 2);     // 문의 1건 15만원
            searchQueryRepository.save(new SearchQueryDaily(acc, day, "c", "g", "법인장기렌트가격", "M", 50, 5, 4_000));
            searchQueryRepository.save(new SearchQueryDaily(acc, day, "c", "g", "무료게임다운", "M", 30, 2, 5_000));

            PerformanceDiagnosis.Result r = diagnosis.diagnose(acc, 30_000);

            assertThat(r.hasData()).isTrue();
            assertThat(r.cost()).isEqualTo(600_000);
            assertThat(r.conversions()).isEqualTo(12);
            assertThat(r.accountCpa()).isEqualTo(50_000);
            assertThat(r.problems()).extracting(PerformanceDiagnosis.Finding::title).contains(
                    "문의 1건 비용이 목표보다 비쌉니다", "전환 없이 광고비만 쓴 키워드가 있습니다",
                    "목표 CPA 를 크게 넘는 키워드가 있습니다", "업종과 관계없어 보이는 검색어로도 광고비가 나갔습니다");
            PerformanceDiagnosis.Finding waste = r.problems().stream()
                    .filter(f -> f.title().startsWith("전환 없이")).findFirst().orElseThrow();
            assertThat(waste.keywords()).containsExactly("법인장기렌트");
            assertThat(waste.amount()).isEqualTo(200_000);
            assertThat(waste.evidence()).contains("200,000원").contains("33%");

            assertThat(r.improvements()).extracting(PerformanceDiagnosis.Finding::title)
                    .contains("목표보다 싸게 문의를 사는 키워드에 더 쓰세요",
                            "실제로 들어온 검색어 중 키워드로 등록 안 된 것이 있습니다");
            assertThat(r.newKeywords()).extracting(PerformanceDiagnosis.KeywordIdea::keyword).containsExactly("법인장기렌트가격");
            assertThat(r.negativeCandidates()).containsExactly("무료게임다운");
        });
    }

    @Test
    void skipsJudgementWhenThereIsTooLittleData() {
        accounts.runAs(9302L, acc -> {
            keyword(acc, "k-tiny", "렌트", LocalDate.now().minusDays(2), 5, 3_000, 0);
            PerformanceDiagnosis.Result r = diagnosis.diagnose(acc, 30_000);
            assertThat(r.hasData()).isFalse();
            assertThat(r.problems()).isEmpty();
            assertThat(r.notes().get(0)).contains("건너뛰었습니다");
        });
    }

    private void keyword(Long acc, String id, String text, LocalDate day, long clicks, long cost, long conversions) {
        keywordRepository.save(new AdKeyword(acc, id, "g", text, "ELIGIBLE", 500L, false));
        statRepository.save(new KeywordStatDaily(acc, id, day, clicks * 20, clicks, cost, 5.0,
                clicks == 0 ? 0 : (double) cost / clicks, 3.0, conversions));
    }
}
