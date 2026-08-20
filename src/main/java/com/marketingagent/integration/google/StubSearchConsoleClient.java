package com.marketingagent.integration.google;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 서치콘솔 설정 전에 화면을 검증하기 위한 Stub.
 * 값은 검색어와 기간으로부터 결정적으로 만든다.
 */
@Component
@ConditionalOnProperty(name = "google.searchconsole.provider", havingValue = "stub", matchIfMissing = true)
public class StubSearchConsoleClient implements SearchConsoleClient {

    private static final List<String> QUERIES = List.of(
            "1톤트럭", "1톤화물차", "중고화물차", "화물차리스", "포터리스",
            "1톤트럭중고", "화물차매매", "윙바디", "냉동탑차", "카고트럭");

    @Override
    public List<SearchConsoleRow> query(LocalDate since, LocalDate until, String dimension, int limit) {
        List<SearchConsoleRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(QUERIES.size(), limit); i++) {
            String key = "page".equals(dimension)
                    ? "https://www.mytruck.kr/" + (i == 0 ? "" : "products#" + i)
                    : QUERIES.get(i);
            int seed = Math.abs((key + since + until).hashCode());
            long impressions = 50 + seed % 2000;
            long clicks = Math.max(1, impressions * (1 + seed % 8) / 100);
            rows.add(new SearchConsoleRow(key, impressions, clicks,
                    round2(clicks * 100.0 / impressions),
                    round2(1.0 + (seed % 250) / 10.0)));
        }
        return rows;
    }

    @Override
    public Health health() {
        return new Health("stub", false, true,
                "Stub 모드입니다. 실제 자연검색 데이터를 보려면 구글 서치콘솔 설정이 필요합니다.");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
