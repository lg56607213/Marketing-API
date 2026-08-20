package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.AdSummary;
import com.marketingagent.domain.ads.dto.DailyPoint;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.dto.KeywordPerformance;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적재된 성과 데이터를 조회용으로 집계한다.
 * CTR/CPC/CVR 은 일자별 평균이 아니라 기간 합계에서 다시 계산한다.
 */
@Service
@RequiredArgsConstructor
public class AdAnalyticsService {

    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final AdGroupRepository adGroupRepository;

    @Transactional(readOnly = true)
    public List<KeywordPerformance> keywordPerformance(LocalDate since, LocalDate until) {
        Map<String, AdKeyword> keywords = keywordRepository.findAll().stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, Function.identity(), (a, b) -> a));
        Map<String, String> adgroupNames = adGroupRepository.findAll().stream()
                .collect(Collectors.toMap(AdGroup::getNccAdgroupId, AdGroup::getName, (a, b) -> a));

        return statRepository.aggregateByKeyword(since, until).stream()
                .map(row -> toPerformance(row, keywords.get(row.nccKeywordId()), adgroupNames))
                .sorted(Comparator.comparingLong(KeywordPerformance::salesAmt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public AdSummary summary(LocalDate since, LocalDate until) {
        List<KeywordAggregate> rows = statRepository.aggregateByKeyword(since, until);

        long impCnt = rows.stream().mapToLong(r -> nz(r.impCnt())).sum();
        long clkCnt = rows.stream().mapToLong(r -> nz(r.clkCnt())).sum();
        long salesAmt = rows.stream().mapToLong(r -> nz(r.salesAmt())).sum();
        long ccnt = rows.stream().mapToLong(r -> nz(r.ccnt())).sum();

        List<DailyPoint> daily = statRepository.aggregateByDate(since, until);

        return new AdSummary(since, until, impCnt, clkCnt,
                ratio(clkCnt * 100.0, impCnt), ratio(salesAmt, clkCnt),
                salesAmt, ccnt, ratio(ccnt * 100.0, clkCnt), ratio(salesAmt, ccnt),
                rows.size(), daily);
    }

    private KeywordPerformance toPerformance(KeywordAggregate row, AdKeyword keyword,
            Map<String, String> adgroupNames) {
        long impCnt = nz(row.impCnt());
        long clkCnt = nz(row.clkCnt());
        long salesAmt = nz(row.salesAmt());
        long ccnt = nz(row.ccnt());

        return new KeywordPerformance(
                row.nccKeywordId(),
                keyword != null ? keyword.getKeyword() : row.nccKeywordId(),
                keyword != null ? adgroupNames.getOrDefault(keyword.getNccAdgroupId(), "-") : "-",
                keyword != null ? keyword.getStatus() : null,
                keyword != null ? keyword.getBidAmt() : null,
                impCnt,
                clkCnt,
                ratio(clkCnt * 100.0, impCnt),
                ratio(salesAmt, clkCnt),
                salesAmt,
                ccnt,
                ratio(ccnt * 100.0, clkCnt),
                round2(row.avgRnk() != null ? row.avgRnk() : 0.0));
    }

    /** 분모가 0이면 0을 돌려준다. 노출·클릭이 없는 키워드에서 나누기가 터지지 않도록. */
    private double ratio(double numerator, long denominator) {
        return denominator == 0 ? 0.0 : round2(numerator / denominator);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
