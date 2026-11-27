package com.marketingagent.domain.subsidy;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.support.MySqlTestDatabases;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** V15 지원사업 테이블을 실제 MySQL 에서 Flyway 로 만들고 Hibernate validate 를 통과하는지, 판정이 저장되는지 본다. */
@Tag("mysql")
@SpringBootTest(properties = {"naver.searchad.provider=stub", "ai.provider=stub"})
class SubsidyMySqlTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabases.register(registry, "subsidy");
    }

    @Autowired
    SubsidyProfileRepository profileRepository;
    @Autowired
    SubsidyAnnouncementRepository announcementRepository;
    @Autowired
    SubsidyMatchRepository matchRepository;
    @Autowired
    SubsidyService service;

    @Test
    void 공고를_분석하고_사업체에_맞춰_판정을_저장한다() {
        SubsidyProfile p = new SubsidyProfile(1L);
        p.update("온니카톡", "법인", LocalDate.now().minusYears(2), "경기", null, "76110", "자동차 임대업",
                500_000_000L, 4, "[\"벤처\"]", 1990, "남", "장기렌트, 마케팅", true, "owner@example.com");
        profileRepository.save(p);

        SubsidyAnnouncement a = new SubsidyAnnouncement("bizinfo", "T-1");
        a.refresh(new SubsidyAnnouncement.Normalized("bizinfo", "T-1", "[경기] 소상공인 온라인 마케팅 지원", "경기도",
                "온라인 광고비 지원", "소상공인", "경기,마케팅", LocalDate.now(), LocalDate.now().plusDays(20),
                "https://www.bizinfo.go.kr/x", "{}"), SubsidyService.dedupeKey("[경기] 소상공인 온라인 마케팅 지원",
                LocalDate.now().plusDays(20)));
        announcementRepository.save(a);

        service.analyze();
        service.matchAll();

        List<SubsidyMatch> matches = matchRepository.findByProfileId(p.getId());
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getVerdict()).isNotEqualTo(SubsidyMatch.NO);
        assertThat(matches.get(0).getReasons()).contains("관심 키워드");
        assertThat(announcementRepository.findById(a.getId()).orElseThrow().getAnalyzedAt()).isNotNull();
    }

    @Test
    void 직접_등록한_공고는_그_업체에만_보인다() {
        SubsidyAnnouncement mine = new SubsidyAnnouncement("manual", "M-1");
        mine.refresh(new SubsidyAnnouncement.Normalized("manual", "M-1", "신보 창업기업 보증", "신용보증기금", null, null,
                "전국", null, null, null, null), "신보창업기업보증|-");
        mine.ownedBy(77L, 1L);
        announcementRepository.save(mine);

        LocalDate today = LocalDate.now();
        assertThat(announcementRepository.findVisible(77L, today, false)).extracting(SubsidyAnnouncement::getSourceId).contains("M-1");
        assertThat(announcementRepository.findVisible(78L, today, false)).extracting(SubsidyAnnouncement::getSourceId).doesNotContain("M-1");
        assertThat(announcementRepository.findBySourceAndCompanyIdOrderByIdDesc("manual", 77L)).hasSize(1);
    }
}
