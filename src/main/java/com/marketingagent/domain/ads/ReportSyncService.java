package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.ReportSyncResult;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.StatReportParser;
import com.marketingagent.integration.naver.dto.ConversionRow;
import com.marketingagent.integration.naver.dto.HourlyStatRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시간대별 성과(AD_DETAIL)와 전환(AD_CONVERSION_DETAIL) 리포트를 내려받아 적재한다.
 *
 * <p>리포트는 하루 단위로만 만들어지므로 날짜별로 반복한다. 한 날짜·한 리포트가 하나의 트랜잭션이라
 * 중간에 하나가 실패해도 나머지는 그대로 남는다.
 *
 * <p>리포트 줄은 소재·지역·매체까지 쪼개져 있어 (시간, 광고그룹, 키워드, 기기) 단위로 합쳐서 저장한다.
 * 그 날짜에 받은 줄이 하나도 없으면 기존 데이터를 건드리지 않는다.
 */
@Slf4j
@Service
public class ReportSyncService {

    /** 간접전환이 뒤늦게 잡히는 것을 감안해도 한 번에 한 달이면 충분하다. */
    static final int MAX_SYNC_DAYS = 31;

    private final SearchAdClient searchAdClient;
    private final KeywordStatHourlyRepository hourlyRepository;
    private final KeywordConversionHourlyRepository conversionRepository;
    private final TransactionTemplate transaction;

    public ReportSyncService(SearchAdClient searchAdClient, KeywordStatHourlyRepository hourlyRepository,
            KeywordConversionHourlyRepository conversionRepository, PlatformTransactionManager transactionManager) {
        this.searchAdClient = searchAdClient;
        this.hourlyRepository = hourlyRepository;
        this.conversionRepository = conversionRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 전일부터 거꾸로 days 일치를 받는다. 당일은 집계가 끝나지 않아 받지 않는다. */
    public ReportSyncResult sync(int days) {
        int span = Math.min(Math.max(days, 1), MAX_SYNC_DAYS);
        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(span - 1L);

        int hourlyRows = 0;
        int conversionRows = 0;
        List<String> failures = new ArrayList<>();

        for (LocalDate date = since; !date.isAfter(until); date = date.plusDays(1)) {
            try {
                hourlyRows += syncHourly(date);
            } catch (Exception e) {
                failures.add(date + " " + StatReportParser.AD_DETAIL + ": " + e.getMessage());
                log.warn("{} 시간대별 성과 리포트를 건너뜁니다: {}", date, e.getMessage());
            }
            try {
                conversionRows += syncConversions(date);
            } catch (Exception e) {
                failures.add(date + " " + StatReportParser.AD_CONVERSION_DETAIL + ": " + e.getMessage());
                log.warn("{} 전환 리포트를 건너뜁니다: {}", date, e.getMessage());
            }
        }

        log.info("리포트 동기화 완료: {} ~ {}, 시간대별 {}행, 전환 {}행, 실패 {}건",
                since, until, hourlyRows, conversionRows, failures.size());
        return new ReportSyncResult(span, since, until, hourlyRows, conversionRows, failures);
    }

    private int syncHourly(LocalDate date) {
        List<KeywordStatHourly> merged = mergeHourly(searchAdClient.hourlyStatReport(date));
        if (merged.isEmpty()) {
            return 0;
        }
        transaction.executeWithoutResult(status -> {
            hourlyRepository.deleteByStatDate(date);
            hourlyRepository.saveAll(merged);
        });
        return merged.size();
    }

    private int syncConversions(LocalDate date) {
        List<KeywordConversionHourly> merged = mergeConversions(searchAdClient.conversionReport(date));
        if (merged.isEmpty()) {
            return 0;
        }
        transaction.executeWithoutResult(status -> {
            conversionRepository.deleteByStatDate(date);
            conversionRepository.saveAll(merged);
        });
        return merged.size();
    }

    static List<KeywordStatHourly> mergeHourly(List<HourlyStatRow> rows) {
        Map<String, long[]> sums = new LinkedHashMap<>();
        Map<String, HourlyStatRow> firsts = new LinkedHashMap<>();
        for (HourlyStatRow row : rows) {
            String key = row.hour() + "|" + row.nccAdgroupId() + "|" + row.nccKeywordId() + "|" + row.device();
            firsts.putIfAbsent(key, row);
            long[] sum = sums.computeIfAbsent(key, k -> new long[4]);
            sum[0] += row.impCnt();
            sum[1] += row.clkCnt();
            sum[2] += row.salesAmt();
            sum[3] += row.rnkSum();
        }
        List<KeywordStatHourly> merged = new ArrayList<>(sums.size());
        sums.forEach((key, sum) -> {
            HourlyStatRow first = firsts.get(key);
            merged.add(new KeywordStatHourly(first.statDate(), first.hour(), first.nccCampaignId(),
                    first.nccAdgroupId(), first.nccKeywordId(), first.device(),
                    sum[0], sum[1], sum[2], sum[3]));
        });
        return merged;
    }

    static List<KeywordConversionHourly> mergeConversions(List<ConversionRow> rows) {
        Map<String, long[]> sums = new LinkedHashMap<>();
        Map<String, ConversionRow> firsts = new LinkedHashMap<>();
        for (ConversionRow row : rows) {
            String key = row.hour() + "|" + row.nccAdgroupId() + "|" + row.nccKeywordId() + "|" + row.device()
                    + "|" + row.convMethod() + "|" + row.convType();
            firsts.putIfAbsent(key, row);
            long[] sum = sums.computeIfAbsent(key, k -> new long[2]);
            sum[0] += row.convCnt();
            sum[1] += row.convAmt();
        }
        List<KeywordConversionHourly> merged = new ArrayList<>(sums.size());
        sums.forEach((key, sum) -> {
            ConversionRow first = firsts.get(key);
            merged.add(new KeywordConversionHourly(first.statDate(), first.hour(), first.nccCampaignId(),
                    first.nccAdgroupId(), first.nccKeywordId(), first.device(),
                    first.convMethod(), first.convType(), sum[0], sum[1]));
        });
        return merged;
    }
}
