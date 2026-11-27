package com.marketingagent.domain.ads.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeywordSelectorTest {

    private final KeywordSelector.Rules rules = new KeywordSelector.Rules(
            List.of("렌트", "장기"), List.of("레이", "저신용"), List.of("중고"),
            List.of("저신용"), List.of("레이"), 1.5, 1.3, 20);

    @Test
    void 상품말과_집중말이_함께_있고_제외말이_없어야_관련있다() {
        assertThat(KeywordSelector.relevant("레이장기렌트", rules)).isTrue();
        assertThat(KeywordSelector.relevant("장기렌트", rules)).isFalse();
        assertThat(KeywordSelector.relevant("레이중고장기렌트", rules)).isFalse();
    }

    @Test
    void 예산_안에서_1원당_가중클릭이_큰_키워드와_입찰가를_고른다() {
        Map<String, RelatedKeyword> stats = new LinkedHashMap<>();
        stats.put("저신용레이장기렌트", new RelatedKeyword("저신용레이장기렌트", 100, 400, 5, 20, "중간"));
        stats.put("레이장기렌트", new RelatedKeyword("레이장기렌트", 1000, 4000, 20, 80, "높음"));
        stats.put("저신용렌트", new RelatedKeyword("저신용렌트", 5, 10, 0, 1, "낮음"));
        Map<String, List<PerformanceEstimate>> curves = Map.of(
                "저신용레이장기렌트", List.of(est("저신용레이장기렌트", 300, 20, 6000), est("저신용레이장기렌트", 1000, 30, 25000)),
                "레이장기렌트", List.of(est("레이장기렌트", 300, 10, 3000), est("레이장기렌트", 3000, 60, 150000)),
                "저신용렌트", List.of(est("저신용렌트", 300, 5, 1000)));

        List<KeywordSelector.Pick> picks = KeywordSelector.select(stats, curves, rules,
                new KeywordSelector.Budget(40000, 3000, 500000, 0.02, 10));

        Map<String, KeywordSelector.Pick> byKeyword = new LinkedHashMap<>();
        picks.forEach(p -> byKeyword.put(p.keyword(), p));
        // 효율이 가장 좋은 저신용 레이는 더 높은 단계까지, 레이장기렌트는 싼 단계만 (3000원 단계는 예산 초과)
        assertThat(byKeyword.get("저신용레이장기렌트").bid()).isEqualTo(1000);
        assertThat(byKeyword.get("레이장기렌트").bid()).isEqualTo(300);
        // 검색량이 너무 적은 키워드는 넣지 않는다
        assertThat(byKeyword.get("저신용렌트").bid()).isZero();
        long spent = picks.stream().mapToLong(KeywordSelector.Pick::cost).sum();
        assertThat(spent).isLessThanOrEqualTo(40000);
    }

    @Test
    void 입찰가를_올려도_클릭이_안_늘면_그_단계는_쓰지_않는다() {
        List<PerformanceEstimate> curve = KeywordSelector.usableCurve(List.of(
                est("a", 500, 10, 4000), est("a", 300, 10, 2500), est("a", 4000, 50, 100000), est("a", 1000, 12, 9000)),
                new KeywordSelector.Budget(1_000_000, 3000, 0, 0.02, 10));
        assertThat(curve).extracting(PerformanceEstimate::bid).containsExactly(300L, 1000L);
    }

    private static PerformanceEstimate est(String k, long bid, long clicks, long cost) {
        return new PerformanceEstimate(k, bid, clicks * 30, clicks, cost);
    }
}
