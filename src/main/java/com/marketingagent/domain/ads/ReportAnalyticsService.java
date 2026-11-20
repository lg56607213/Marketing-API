package com.marketingagent.domain.ads;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.dto.HourOfDayPerformance;
import com.marketingagent.domain.ads.dto.HourlyAggregate;
import com.marketingagent.domain.ads.dto.HourlyConversionAggregate;
import com.marketingagent.domain.ads.dto.KeywordConversionAggregate;
import com.marketingagent.domain.ads.dto.KeywordConversionPerformance;
import com.marketingagent.domain.ads.dto.KeywordCostAggregate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.marketingagent.domain.ads.account.AdAccountService;

/**
 * 적재된 시간대별 성과와 전환을 묶어 비용 대비 성과를 계산한다.
 * 비율 지표는 모두 합계에서 다시 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportAnalyticsService {

    private final AdAccountService accountService;
    private final KeywordStatHourlyRepository hourlyRepository;
    private final KeywordConversionHourlyRepository conversionRepository;
    private final AdKeywordRepository keywordRepository;

    /**
     * 하루 중 시간대별 성과. 항상 0~23시 24줄을 돌려준다.
     *
     * @param device P, M 또는 null(전체)
     */
    public List<HourOfDayPerformance> hourOfDay(Long accountId, LocalDate since, LocalDate until, String device) {
        Long account = accountService.require(accountId).getId();
        List<HourlyAggregate> stats = hourlyRepository.aggregateByHour(account, since, until);
        if (stats.isEmpty()) {
            throw new BadRequestException("적재된 시간대별 성과가 없습니다. 먼저 리포트 동기화를 실행하세요.");
        }

        long[][] sums = new long[24][6];
        for (HourlyAggregate row : stats) {
            if (!matches(device, row.device()) || !validHour(row.hour())) {
                continue;
            }
            long[] sum = sums[row.hour()];
            sum[0] += nz(row.impCnt());
            sum[1] += nz(row.clkCnt());
            sum[2] += nz(row.salesAmt());
            sum[3] += nz(row.rnkSum());
        }
        for (HourlyConversionAggregate row : conversionRepository.aggregateByHour(account, since, until)) {
            if (!matches(device, row.device()) || !validHour(row.hour())) {
                continue;
            }
            long[] sum = sums[row.hour()];
            sum[4] += nz(row.convCnt());
            sum[5] += nz(row.convAmt());
        }

        List<HourOfDayPerformance> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            long[] s = sums[hour];
            result.add(new HourOfDayPerformance(hour, s[0], s[1], s[2],
                    ratio(s[1] * 100.0, s[0]), ratio(s[2], s[1]), ratio(s[3], s[0]),
                    s[4], s[5], ratio(s[2], s[4]), ratio(s[5] * 100.0, s[2])));
        }
        return result;
    }

    /** 키워드별 비용 대비 전환 성과. 광고비 내림차순. */
    public List<KeywordConversionPerformance> keywordConversions(Long accountId, LocalDate since, LocalDate until) {
        Long account = accountService.require(accountId).getId();
        List<KeywordCostAggregate> costs = hourlyRepository.aggregateByKeyword(account, since, until);
        if (costs.isEmpty()) {
            throw new BadRequestException("적재된 시간대별 성과가 없습니다. 먼저 리포트 동기화를 실행하세요.");
        }

        // [전체 전환수, 직접전환수, 구매 전환수, 전환매출액]
        Map<String, long[]> conversions = new HashMap<>();
        for (KeywordConversionAggregate row : conversionRepository.aggregateByKeyword(account, since, until)) {
            long[] sum = conversions.computeIfAbsent(row.nccKeywordId(), k -> new long[4]);
            long count = nz(row.convCnt());
            sum[0] += count;
            if (row.convMethod() != null && row.convMethod() == KeywordConversionHourly.METHOD_DIRECT) {
                sum[1] += count;
            }
            if (KeywordConversionHourly.TYPE_PURCHASE.equals(row.convType())) {
                sum[2] += count;
            }
            sum[3] += nz(row.convAmt());
        }

        Map<String, String> names = keywordRepository.findByAdAccountId(account).stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, AdKeyword::getKeyword, (a, b) -> a));

        return costs.stream()
                .map(cost -> {
                    long[] conv = conversions.getOrDefault(cost.nccKeywordId(), new long[4]);
                    long salesAmt = nz(cost.salesAmt());
                    return new KeywordConversionPerformance(
                            cost.nccKeywordId(), names.get(cost.nccKeywordId()),
                            nz(cost.impCnt()), nz(cost.clkCnt()), salesAmt,
                            conv[0], conv[1], conv[2], conv[3],
                            ratio(salesAmt, conv[0]), ratio(conv[3] * 100.0, salesAmt));
                })
                .sorted(Comparator.comparingLong(KeywordConversionPerformance::salesAmt).reversed())
                .toList();
    }

    private boolean matches(String wanted, String actual) {
        return wanted == null || wanted.isBlank() || wanted.equalsIgnoreCase(actual);
    }

    private boolean validHour(Integer hour) {
        return hour != null && hour >= 0 && hour <= 23;
    }

    private double ratio(double numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round(numerator / denominator * 100.0) / 100.0;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
