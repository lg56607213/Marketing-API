package com.marketingagent.integration.naver;

import com.marketingagent.integration.naver.dto.ConversionRow;
import com.marketingagent.integration.naver.dto.HourlyStatRow;
import com.marketingagent.integration.naver.dto.SearchQueryRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 네이버 대용량 보고서(탭 구분 텍스트) 파서.
 *
 * <p>컬럼 순서는 공식 문서의 Stat Report Specification 을 따른다
 * (https://naver.github.io/searchad-apidoc/#/tags/StatReport).
 * 컬럼 수가 모자란 줄과 머리글로 보이는 줄은 건너뛴다.
 */
public final class StatReportParser {

    public static final String AD_DETAIL = "AD_DETAIL";
    public static final String AD_CONVERSION_DETAIL = "AD_CONVERSION_DETAIL";
    public static final String EXPKEYWORD = "EXPKEYWORD";

    /** 문서 기준 컬럼 수. 실계정 점검에서 실제 응답과 비교한다. */
    public static final int AD_DETAIL_COLUMNS = 16;
    public static final int AD_CONVERSION_DETAIL_COLUMNS = 15;
    public static final int EXPKEYWORD_COLUMNS = 12;

    private StatReportParser() {
    }

    /** 문서 기준 컬럼 수. 모르는 리포트 종류면 0. */
    public static int expectedColumns(String reportTp) {
        return switch (reportTp) {
            case AD_DETAIL -> AD_DETAIL_COLUMNS;
            case AD_CONVERSION_DETAIL -> AD_CONVERSION_DETAIL_COLUMNS;
            case EXPKEYWORD -> EXPKEYWORD_COLUMNS;
            default -> 0;
        };
    }

    /** 본문을 줄·탭으로 나눈다. 빈 줄과 머리글 줄은 버린다. */
    public static List<String[]> split(String body) {
        List<String[]> lines = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return lines;
        }
        for (String line : body.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            // 첫 컬럼은 날짜다. 숫자로 시작하지 않으면 머리글로 본다.
            String first = columns[0].trim();
            if (first.isEmpty() || !Character.isDigit(first.charAt(0))) {
                continue;
            }
            lines.add(columns);
        }
        return lines;
    }

    /**
     * AD_DETAIL: 1 날짜, 2 고객ID, 3 캠페인, 4 광고그룹, 5 키워드, 6 소재, 7 비즈채널, 8 시간,
     * 9 지역, 10 매체, 11 PC/모바일, 12 노출, 13 클릭, 14 비용, 15 순위합, 16 조회수.
     */
    public static List<HourlyStatRow> hourlyStats(LocalDate date, String body) {
        List<HourlyStatRow> rows = new ArrayList<>();
        for (String[] c : split(body)) {
            if (c.length < 15) {
                continue;
            }
            int hour = hour(c[7]);
            if (hour < 0) {
                continue;
            }
            rows.add(new HourlyStatRow(date, hour, c[2].trim(), c[3].trim(), c[4].trim(), c[10].trim(),
                    number(c[11]), number(c[12]), number(c[13]), number(c[14])));
        }
        return rows;
    }

    /**
     * AD_CONVERSION_DETAIL: 1 날짜, 2 고객ID, 3 캠페인, 4 광고그룹, 5 키워드, 6 소재, 7 비즈채널, 8 시간,
     * 9 지역, 10 매체, 11 PC/모바일, 12 전환방식(1 직접, 2 간접), 13 전환유형, 14 전환수, 15 전환매출.
     */
    public static List<ConversionRow> conversions(LocalDate date, String body) {
        List<ConversionRow> rows = new ArrayList<>();
        for (String[] c : split(body)) {
            if (c.length < 15) {
                continue;
            }
            int hour = hour(c[7]);
            if (hour < 0) {
                continue;
            }
            rows.add(new ConversionRow(date, hour, c[2].trim(), c[3].trim(), c[4].trim(), c[10].trim(),
                    (int) number(c[11]), c[12].trim(), number(c[13]), number(c[14])));
        }
        return rows;
    }

    /**
     * EXPKEYWORD: 1 날짜, 2 고객ID, 3 캠페인, 4 광고그룹, 5 검색어, 6 매체, 7 PC/모바일,
     * 8 검색어 유형, 9 노출, 10 클릭, 11 비용, 12 조회수.
     */
    public static List<SearchQueryRow> searchQueries(LocalDate date, String body) {
        List<SearchQueryRow> rows = new ArrayList<>();
        for (String[] c : split(body)) {
            if (c.length < 11) {
                continue;
            }
            rows.add(new SearchQueryRow(date, c[2], c[3], c[4], c[6],
                    number(c[8]), number(c[9]), number(c[10])));
        }
        return rows;
    }

    /** 비용은 2026-03-30 이전 데이터가 소수로 내려오므로 소수도 받아 정수로 내린다. */
    static long number(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }

    /** 0~23 이 아니면 -1. */
    static int hour(String value) {
        try {
            int hour = Integer.parseInt(value.trim());
            return hour >= 0 && hour <= 23 ? hour : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
