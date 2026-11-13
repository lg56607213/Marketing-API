package com.marketingagent.integration.naver;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketingagent.integration.naver.dto.ConversionRow;
import com.marketingagent.integration.naver.dto.HourlyStatRow;
import com.marketingagent.integration.naver.dto.SearchQueryRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 줄 구성은 공식 문서의 Stat Report Specification 컬럼 순서를 그대로 따른다. */
class StatReportParserTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 12);

    private static String line(String... columns) {
        return String.join("\t", columns);
    }

    @Test
    void parsesAdDetailColumnsInDocumentedOrder() {
        String body = line("20261112", "1234567", "cmp-1", "grp-1", "nkw-1", "nad-1", "bsn-1",
                "09", "R01", "27758", "M", "120", "7", "4900", "250", "0") + "\n";

        List<HourlyStatRow> rows = StatReportParser.hourlyStats(DATE, body);

        assertThat(rows).containsExactly(
                new HourlyStatRow(DATE, 9, "cmp-1", "grp-1", "nkw-1", "M", 120, 7, 4900, 250));
        assertThat(StatReportParser.split(body).get(0)).hasSize(StatReportParser.AD_DETAIL_COLUMNS);
    }

    @Test
    void parsesConversionDetailColumnsInDocumentedOrder() {
        String body = line("20261112", "1234567", "cmp-1", "grp-1", "nkw-1", "nad-1", "bsn-1",
                "21", "R01", "27758", "P", "2", "purchase", "3", "150000") + "\r\n";

        List<ConversionRow> rows = StatReportParser.conversions(DATE, body);

        assertThat(rows).containsExactly(
                new ConversionRow(DATE, 21, "cmp-1", "grp-1", "nkw-1", "P", 2, "purchase", 3, 150000));
        assertThat(StatReportParser.split(body).get(0)).hasSize(StatReportParser.AD_CONVERSION_DETAIL_COLUMNS);
    }

    @Test
    void parsesSearchTermColumnsInDocumentedOrder() {
        String body = line("20261112", "1234567", "cmp-1", "grp-1", "중고 화물차 가격", "27758", "M",
                "1", "40", "3", "2100", "0");

        List<SearchQueryRow> rows = StatReportParser.searchQueries(DATE, body);

        assertThat(rows).containsExactly(
                new SearchQueryRow(DATE, "cmp-1", "grp-1", "중고 화물차 가격", "M", 40, 3, 2100));
        assertThat(StatReportParser.split(body).get(0)).hasSize(StatReportParser.EXPKEYWORD_COLUMNS);
    }

    @Test
    void acceptsDecimalCostFromOlderData() {
        String body = line("20260101", "1234567", "cmp-1", "grp-1", "nkw-1", "nad-1", "bsn-1",
                "0", "R01", "27758", "P", "10", "1", "770.0", "12", "0");

        assertThat(StatReportParser.hourlyStats(DATE, body).get(0).salesAmt()).isEqualTo(770);
    }

    @Test
    void skipsHeaderBlankShortAndBadHourLines() {
        String body = String.join("\n",
                line("Date", "CUSTOMER ID", "Campaign ID"),
                "",
                line("20261112", "1234567", "cmp-1"),
                line("20261112", "1234567", "cmp-1", "grp-1", "nkw-1", "nad-1", "bsn-1",
                        "99", "R01", "27758", "P", "10", "1", "700", "12", "0"));

        assertThat(StatReportParser.hourlyStats(DATE, body)).isEmpty();
        assertThat(StatReportParser.conversions(DATE, "")).isEmpty();
        assertThat(StatReportParser.split(null)).isEmpty();
    }
}
