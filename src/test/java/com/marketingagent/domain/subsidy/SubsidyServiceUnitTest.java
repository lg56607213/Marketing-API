package com.marketingagent.domain.subsidy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubsidyServiceUnitTest {

    @Test
    void 같은_공고는_소스가_달라도_같은_키로_묶인다() {
        LocalDate end = LocalDate.of(2027, 1, 31);
        String a = SubsidyService.dedupeKey("[창업진흥원] 2027년 예비창업패키지 모집 공고", end);
        String b = SubsidyService.dedupeKey("2027년 예비창업패키지  모집 공고(재공고 아님)", end);
        assertThat(a).isEqualTo(b);
        assertThat(SubsidyService.dedupeKey("2027년 예비창업패키지 모집 공고", LocalDate.of(2027, 2, 1))).isNotEqualTo(a);
    }

    @Test
    void 받는사람은_쉼표_공백으로_나누고_메일주소만_남긴다() {
        SubsidyProfile p = new SubsidyProfile(1L);
        p.update("테스트", "개인", null, null, null, null, null, null, null, null, null, null, null, true,
                "a@x.com, b@y.co.kr;잘못된값");
        assertThat(SubsidyService.recipients(p)).containsExactly("a@x.com", "b@y.co.kr");
    }

    @Test
    void AI없이도_지역은_규칙으로_뽑고_나머지는_모름으로_둔다() {
        SubsidyAnnouncement a = new SubsidyAnnouncement("bizinfo", "1");
        a.refresh(new SubsidyAnnouncement.Normalized("bizinfo", "1", "[부산] 소상공인 지원", null, "요약", null,
                "부산,소상공인", null, null, null, null), "k");
        Eligibility e = SubsidyAi.byRules(a);
        assertThat(e.regions()).containsExactly("부산");
        assertThat(e.maxBizAgeYears()).isNull();
        assertThat(e.entityTypes()).isNull();
    }
}
