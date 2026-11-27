package com.marketingagent.domain.ads.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.ads.launch.ProposalDtos.Item;
import com.marketingagent.domain.ads.launch.ProposalDtos.Option;
import com.marketingagent.domain.ads.launch.ProposalDtos.Summary;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalInsightsTest {

    @Test
    void 비싸서_빠진_핵심_키워드와_얼마부터_되는지를_알려준다() {
        Item pick = new Item("레이리스", 820, 3.5, "높음", 1.3, "PICK", 1500, 20, 30000, "추천", false, null,
                List.of(new Option(1500, 20, 30000)), 0, 0, 0);
        Item over = new Item("레이장기렌트", 2380, 1.9, "높음", 1.3, "OVER_CAP", 0, 0, 0, "비쌈", false, null,
                List.of(), 8000, 40, 300000);
        Summary s = new Summary(2, 1, 20, 30000, 1500, 0.5, 220000, 1_500_000, 6000, 2.0, 500000);

        List<String> out = KeywordProposalService.insights(List.of(pick, over), s, 0.02, 500000);

        assertThat(out).anyMatch(t -> t.contains("후보 2개 중 1개를 추천"));
        assertThat(out).anyMatch(t -> t.contains("레이장기렌트") && t.contains("8,000원부터 월 40클릭"));
        assertThat(out).anyMatch(t -> t.contains("예산") && t.contains("%만 씁니다"));
    }
}
