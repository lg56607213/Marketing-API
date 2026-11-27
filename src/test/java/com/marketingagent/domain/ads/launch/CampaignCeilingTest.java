package com.marketingagent.domain.ads.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.ads.autopilot.AutopilotGoals;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CampaignCeilingTest {

    @Test
    void 캠페인에_정한_최대금액이_계정_기본값보다_먼저다() {
        AutopilotGoals.Ceiling ceiling = new AutopilotGoals.Ceiling(3300, Map.of("grp-ray", 3000L));

        assertThat(ceiling.of("grp-ray")).isEqualTo(3000);
        assertThat(ceiling.of("grp-truck")).isEqualTo(3300);
        assertThat(ceiling.of(null)).isEqualTo(3300);
        assertThat(ceiling.any()).isTrue();
    }

    @Test
    void 계정_기본값이_없어도_캠페인_기준이_있으면_움직인다() {
        AutopilotGoals.Ceiling ceiling = new AutopilotGoals.Ceiling(0, Map.of("grp-ray", 3000L));

        assertThat(ceiling.any()).isTrue();
        assertThat(ceiling.of("grp-truck")).isZero();
        assertThat(new AutopilotGoals.Ceiling(0, Map.of()).any()).isFalse();
    }

    @Test
    void 사이트_주소는_www_와_경로를_무시하고_도메인으로_비교한다() {
        assertThat(AdLaunchService.host("https://www.onlycartalk.com/ray")).isEqualTo("onlycartalk.com");
        assertThat(AdLaunchService.host("onlycartalk.com")).isEqualTo("onlycartalk.com");
        assertThat(AdLaunchService.host("http://WWW.OnlyCartalk.com")).isEqualTo("onlycartalk.com");
        assertThat(AdLaunchService.host("")).isEmpty();
    }
}
