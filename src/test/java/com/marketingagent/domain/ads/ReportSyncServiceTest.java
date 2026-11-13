package com.marketingagent.domain.ads;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.domain.ads.dto.DiagnosticReport;
import com.marketingagent.domain.ads.dto.HourOfDayPerformance;
import com.marketingagent.domain.ads.dto.KeywordConversionPerformance;
import com.marketingagent.domain.ads.dto.ReportSyncResult;
import com.marketingagent.integration.naver.dto.ConversionRow;
import com.marketingagent.integration.naver.dto.HourlyStatRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Stub 클라이언트로 수집 → 적재 → 집계까지 한 번에 확인한다. 파일 DB 를 건드리지 않게 메모리 DB 를 쓴다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reportsync;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "naver.searchad.provider=stub"
})
class ReportSyncServiceTest {

    @Autowired
    private AdSyncService adSyncService;
    @Autowired
    private ReportSyncService reportSyncService;
    @Autowired
    private ReportAnalyticsService analyticsService;
    @Autowired
    private AdDiagnosticsService diagnosticsService;
    @Autowired
    private KeywordStatHourlyRepository hourlyRepository;
    @Autowired
    private KeywordConversionHourlyRepository conversionRepository;

    @Test
    void syncIsRepeatableAndFeedsAnalytics() {
        adSyncService.sync(1);

        ReportSyncResult first = reportSyncService.sync(2);
        long hourlyAfterFirst = hourlyRepository.count();
        long conversionsAfterFirst = conversionRepository.count();
        ReportSyncResult second = reportSyncService.sync(2);

        // 10개 키워드 x 24시간 x 2기기 x 2일
        assertThat(first.failures()).isEmpty();
        assertThat(first.hourlyRows()).isEqualTo(960);
        assertThat(first.conversionRows()).isPositive();
        assertThat(second.hourlyRows()).isEqualTo(first.hourlyRows());
        assertThat(hourlyRepository.count()).isEqualTo(hourlyAfterFirst).isEqualTo(960);
        assertThat(conversionRepository.count()).isEqualTo(conversionsAfterFirst);

        LocalDate until = LocalDate.now().minusDays(1);
        LocalDate since = until.minusDays(1);

        List<HourOfDayPerformance> hours = analyticsService.hourOfDay(since, until, null);
        assertThat(hours).hasSize(24);
        assertThat(hours.stream().mapToLong(HourOfDayPerformance::impCnt).sum())
                .isEqualTo(hourlyRepository.findAll().stream().mapToLong(KeywordStatHourly::getImpCnt).sum());
        assertThat(hours.stream().mapToLong(HourOfDayPerformance::convCnt).sum())
                .isEqualTo(conversionRepository.findAll().stream()
                        .mapToLong(KeywordConversionHourly::getConvCnt).sum());
        long mobileImp = analyticsService.hourOfDay(since, until, "M").stream()
                .mapToLong(HourOfDayPerformance::impCnt).sum();
        long pcImp = analyticsService.hourOfDay(since, until, "P").stream()
                .mapToLong(HourOfDayPerformance::impCnt).sum();
        assertThat(mobileImp + pcImp).isEqualTo(hours.stream().mapToLong(HourOfDayPerformance::impCnt).sum());

        List<KeywordConversionPerformance> keywords = analyticsService.keywordConversions(since, until);
        assertThat(keywords).hasSize(10);
        assertThat(keywords).allSatisfy(k -> assertThat(k.keyword()).isNotBlank());
        assertThat(keywords).isSortedAccordingTo((a, b) -> Long.compare(b.salesAmt(), a.salesAmt()));
        // Stub 은 홀수 번째 키워드에 전환을 주지 않는다.
        assertThat(keywords).anySatisfy(k -> {
            assertThat(k.convCnt()).isZero();
            assertThat(k.cpa()).isZero();
        });
        assertThat(keywords).anySatisfy(k -> assertThat(k.convCnt()).isPositive());
        assertThat(keywords).allSatisfy(k -> assertThat(k.directConvCnt()).isLessThanOrEqualTo(k.convCnt()));
    }

    @Test
    void mergesRowsThatDifferOnlyByAdOrRegion() {
        LocalDate date = LocalDate.of(2026, 11, 12);
        List<KeywordStatHourly> stats = ReportSyncService.mergeHourly(List.of(
                new HourlyStatRow(date, 9, "cmp", "grp", "nkw", "M", 10, 1, 500, 20),
                new HourlyStatRow(date, 9, "cmp", "grp", "nkw", "M", 5, 2, 900, 15),
                new HourlyStatRow(date, 9, "cmp", "grp", "nkw", "P", 3, 0, 0, 9)));

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).getImpCnt()).isEqualTo(15);
        assertThat(stats.get(0).getClkCnt()).isEqualTo(3);
        assertThat(stats.get(0).getSalesAmt()).isEqualTo(1400);
        assertThat(stats.get(0).getRnkSum()).isEqualTo(35);

        List<KeywordConversionHourly> conversions = ReportSyncService.mergeConversions(List.of(
                new ConversionRow(date, 9, "cmp", "grp", "nkw", "M", 1, "purchase", 1, 30000),
                new ConversionRow(date, 9, "cmp", "grp", "nkw", "M", 1, "purchase", 2, 50000),
                new ConversionRow(date, 9, "cmp", "grp", "nkw", "M", 2, "purchase", 1, 10000)));

        assertThat(conversions).hasSize(2);
        assertThat(conversions.get(0).getConvCnt()).isEqualTo(3);
        assertThat(conversions.get(0).getConvAmt()).isEqualTo(80000);
    }

    @Test
    void diagnosticsPassInStubModeButFlagIt() {
        DiagnosticReport report = diagnosticsService.run();

        assertThat(report.ready()).isTrue();
        assertThat(report.checks()).extracting(DiagnosticReport.Check::status).doesNotContain("FAIL", "SKIP");
        assertThat(report.checks().get(0).status()).isEqualTo("WARN");
        assertThat(report.checks()).extracting(DiagnosticReport.Check::name)
                .contains("리포트 AD_DETAIL", "리포트 AD_CONVERSION_DETAIL", "리포트 EXPKEYWORD");
    }
}
