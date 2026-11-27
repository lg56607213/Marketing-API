package com.marketingagent.domain.subsidy;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.subsidy.EligibilityMatcher.ProfileFacts;
import com.marketingagent.domain.subsidy.EligibilityMatcher.Result;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EligibilityMatcherTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    /** 경기 소재 법인, 3년차 자동차 임대업, 대표 35세 */
    private final ProfileFacts rental = new ProfileFacts("법인", LocalDate.of(2023, 6, 1), "경기도", "76110",
            "자동차 임대업", 800_000_000L, 5, List.of("벤처"), 1991, "남", "장기렌트, 마케팅, 온라인 광고");

    private static Eligibility elig(List<String> regions, List<String> types, Double minAge, Double maxAge,
            List<String> targets, List<String> excluded, Long maxRevenue, List<String> required,
            List<String> preferred, Integer ceoAgeMax) {
        return new Eligibility(regions, types, minAge, maxAge, false, targets, excluded, maxRevenue, null, required,
                preferred, ceoAgeMax, "자금", "최대 1억원", "요약");
    }

    @Test
    void 조건을_모두_만족하고_업종이_맞으면_적합이다() {
        Eligibility e = elig(List.of("전국"), List.of("법인", "개인"), null, 7.0, List.of("자동차 임대"),
                List.of("금융", "보험", "대부"), null, List.of(), List.of("청년대표", "벤처"), null);

        Result r = EligibilityMatcher.judge(rental, e, "중소기업 정책자금", "운전자금 지원", LocalDate.of(2026, 12, 30), TODAY);

        assertThat(r.verdict()).isEqualTo(SubsidyMatch.FIT);
        assertThat(r.score()).isEqualTo(50 + 20 + 5 + 5);
        assertThat(r.reasons()).anyMatch(s -> s.contains("업력 조건"));
        assertThat(r.blockers()).isEmpty();
    }

    @Test
    void 지역이_다르면_탈락하고_이유를_남긴다() {
        Eligibility e = elig(List.of("부산광역시"), null, null, null, null, null, null, null, null, null);

        Result r = EligibilityMatcher.judge(rental, e, "부산 소상공인 지원", null, null, TODAY);

        assertThat(r.verdict()).isEqualTo(SubsidyMatch.NO);
        assertThat(r.blockers()).anyMatch(s -> s.contains("지역 제한"));
    }

    @Test
    void 경기도와_경기는_같은_지역이다() {
        Eligibility e = elig(List.of("경기"), null, null, null, null, null, null, null, null, null);

        Result r = EligibilityMatcher.judge(rental, e, "경기 지원", null, null, TODAY);

        assertThat(r.verdict()).isNotEqualTo(SubsidyMatch.NO);
    }

    @Test
    void 업력_초과_대표나이_초과_필수인증_없음은_각각_탈락이다() {
        assertThat(EligibilityMatcher.judge(rental, elig(null, null, null, 2.0, null, null, null, null, null, null),
                "t", null, null, TODAY).blockers()).anyMatch(s -> s.contains("업력"));
        assertThat(EligibilityMatcher.judge(rental, elig(null, null, null, null, null, null, null, null, null, 30),
                "t", null, null, TODAY).verdict()).isEqualTo(SubsidyMatch.NO);
        assertThat(EligibilityMatcher.judge(rental, elig(null, null, null, null, null, null, null, List.of("이노비즈"), null, null),
                "t", null, null, TODAY).blockers()).anyMatch(s -> s.contains("이노비즈"));
    }

    @Test
    void 금융업은_제외업종에_걸린다() {
        ProfileFacts lender = new ProfileFacts("법인", LocalDate.of(2020, 1, 1), "서울", "64920", "대부업", null, null,
                null, null, null, null);
        Eligibility e = elig(List.of("전국"), null, null, null, null, List.of("금융", "보험", "대부"), null, null, null, null);

        assertThat(EligibilityMatcher.judge(lender, e, "정책자금", null, null, TODAY).verdict()).isEqualTo(SubsidyMatch.NO);
    }

    @Test
    void 마감이_지났으면_탈락이다() {
        Result r = EligibilityMatcher.judge(rental, null, "t", null, LocalDate.of(2026, 11, 1), TODAY);
        assertThat(r.verdict()).isEqualTo(SubsidyMatch.NO);
    }

    @Test
    void 사업체_정보가_비어_판단을_못하면_애매로_두고_무엇이_비었는지_알려준다() {
        ProfileFacts blank = new ProfileFacts("개인", null, null, null, null, null, null, null, null, null, null);
        Eligibility e = elig(List.of("서울"), null, null, 7.0, null, null, null, null, null, 39);

        Result r = EligibilityMatcher.judge(blank, e, "t", null, null, TODAY);

        assertThat(r.verdict()).isEqualTo(SubsidyMatch.MAYBE);
        assertThat(r.unknowns()).isEqualTo(3);
        assertThat(r.blockers()).anyMatch(s -> s.contains("개업일"));
    }

    @Test
    void 예비창업자는_허용된_공고에만_맞는다() {
        ProfileFacts pre = new ProfileFacts("예비창업자", null, "서울", null, null, null, null, null, 1995, null, "창업");
        Eligibility notOk = elig(null, List.of("법인", "개인"), null, null, null, null, null, null, null, null);
        Eligibility ok = new Eligibility(null, null, null, 3.0, true, null, null, null, null, null, null, null,
                "사업화", null, null);

        assertThat(EligibilityMatcher.judge(pre, notOk, "t", null, null, TODAY).verdict()).isEqualTo(SubsidyMatch.NO);
        assertThat(EligibilityMatcher.judge(pre, ok, "예비창업 패키지", null, null, TODAY).verdict()).isNotEqualTo(SubsidyMatch.NO);
    }

    @Test
    void 마감_7일_이내면_임박으로_표시한다() {
        Eligibility e = elig(List.of("전국"), null, null, null, null, null, null, null, null, null);
        Result r = EligibilityMatcher.judge(rental, e, "t", null, LocalDate.of(2026, 12, 2), TODAY);
        assertThat(r.reasons()).anyMatch(s -> s.contains("D-5"));
    }
}
