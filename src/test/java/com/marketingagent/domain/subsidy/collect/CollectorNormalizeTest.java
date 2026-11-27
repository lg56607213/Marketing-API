package com.marketingagent.domain.subsidy.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.subsidy.SubsidyAnnouncement.Normalized;
import com.marketingagent.domain.subsidy.SubsidyProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 가정 샘플 기준. 실제 응답 샘플이 생기면 같은 테스트를 실제 파일로도 돌린다. */
class CollectorNormalizeTest {

    private final SubsidyProperties props = new SubsidyProperties(true, "", "", "", "", "", "", 10, 5, 1);
    private final ObjectMapper om = new ObjectMapper();

    private String fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/subsidy/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 기업마당_응답을_공통모양으로_바꾸고_마감지난_공고와_ID없는_줄은_뺀다() throws Exception {
        List<Normalized> rows = new BizinfoCollector(props, om).parse(fixture("bizinfo-assumed.json"));

        assertThat(rows).hasSize(1);
        Normalized n = rows.get(0);
        assertThat(n.sourceId()).isEqualTo("PBLN_000000000100001");
        assertThat(n.agency()).isEqualTo("경기도 / 경기도경제과학진흥원");
        assertThat(n.summary()).doesNotContain("<p>").contains("300만원");
        assertThat(n.applyStart()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(n.applyEnd()).isEqualTo(LocalDate.of(2099, 12, 31));
        assertThat(n.url()).startsWith("https://www.bizinfo.go.kr/");
        assertThat(n.regionText()).contains("경기");
    }

    @Test
    void 기업마당_실제_응답_샘플의_필드를_빠짐없이_읽는다() throws Exception {
        BizinfoCollector c = new BizinfoCollector(props, om);
        var items = om.readTree(fixture("bizinfo-real.json")).path("jsonArray");
        assertThat(items.size()).isEqualTo(5);

        Normalized first = c.normalize(items.get(0));
        assertThat(first.sourceId()).isEqualTo("PBLN_000000000128177");
        assertThat(first.title()).contains("청년창업농");
        assertThat(first.agency()).isEqualTo("경상남도"); // "기초자치단체"는 붙이지 않는다
        assertThat(first.applyStart()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(first.applyEnd()).isEqualTo(LocalDate.of(2026, 12, 22));
        assertThat(first.url()).startsWith("https://www.bizinfo.go.kr/");
        assertThat(first.regionText()).contains("경남");
        assertThat(first.targetText()).contains("창업벤처").contains("분야: 창업 > 사업화지원");
        assertThat(first.summary()).doesNotContain("<p>").contains("신청 방법").contains("문의");

        Normalized third = c.normalize(items.get(2));
        assertThat(third.agency()).isEqualTo("경기도 / 경기도경제과학진흥원");
    }

    @Test
    void K스타트업_응답을_공통모양으로_바꾼다() throws Exception {
        List<Normalized> rows = new KstartupCollector(props, om).parse(fixture("kstartup-assumed.json"));

        assertThat(rows).hasSize(1);
        Normalized n = rows.get(0);
        assertThat(n.sourceId()).isEqualTo("175000");
        assertThat(n.title()).contains("예비창업패키지");
        assertThat(n.targetText()).contains("예비창업자").contains("만 39세 이하");
        assertThat(n.regionText()).isEqualTo("전국");
        assertThat(n.applyEnd()).isEqualTo(LocalDate.of(2099, 12, 31));
    }

    @Test
    void 날짜는_여러_형식에서_뽑는다() {
        assertThat(Fields.date("2026.01.02 ~ 2026.02.03", 1)).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(Fields.date("상시 접수", 0)).isNull();
        assertThat(Fields.date("20260105", 0)).isEqualTo(LocalDate.of(2026, 1, 5));
    }
}
