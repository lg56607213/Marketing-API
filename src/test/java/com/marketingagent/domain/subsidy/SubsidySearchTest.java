package com.marketingagent.domain.subsidy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubsidySearchTest {

    private static SubsidyAnnouncement ann(String title, String target, String summary) {
        SubsidyAnnouncement a = new SubsidyAnnouncement("bizinfo", title);
        a.refresh(new SubsidyAnnouncement.Normalized("bizinfo", title, title, "중기부", summary, target, "전국",
                null, null, null, null), "k");
        return a;
    }

    @Test
    void 검색어는_공백_쉼표로_나누고_띄어쓰기는_무시한다() {
        assertThat(SubsidyCatalogService.words(" 렌터카,  온라인 마케팅 ")).containsExactly("렌터카", "온라인", "마케팅");
        assertThat(SubsidyCatalogService.words("")).isEmpty();
    }

    @Test
    void 제목에_나오면_3점_본문에_나오면_1점이다() {
        SubsidyAnnouncement a = ann("소상공인 온라인 마케팅 지원", "소상공인", "광고비 지원");
        assertThat(SubsidyCatalogService.hits(a, List.of("마케팅"))).isEqualTo(3);
        assertThat(SubsidyCatalogService.hits(a, List.of("광고비"))).isEqualTo(1);
        assertThat(SubsidyCatalogService.hits(a, List.of("마케팅", "광고비", "수출"))).isEqualTo(4);
        assertThat(SubsidyCatalogService.hits(a, List.of("수출"))).isZero();
    }

    @Test
    void 직접_등록한_공고는_같은_제목의_공용_공고를_가리지_않는다() {
        SubsidyAnnouncement shared = ann("정책자금", null, null);
        SubsidyAnnouncement mine = new SubsidyAnnouncement("manual", "m1");
        mine.refresh(new SubsidyAnnouncement.Normalized("manual", "m1", "정책자금", null, null, null, null, null, null,
                null, null), "k");
        mine.ownedBy(7L, 1L);

        assertThat(SubsidyService.representatives(List.of(shared, mine))).hasSize(2);
    }
}
