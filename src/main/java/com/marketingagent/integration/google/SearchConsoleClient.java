package com.marketingagent.integration.google;

import java.time.LocalDate;
import java.util.List;

/**
 * 구글 서치콘솔 자연검색 성과 조회.
 * 검색광고와 달리 광고비가 없고, 노출 · 클릭 · CTR · 평균순위만 제공한다.
 */
public interface SearchConsoleClient {

    /**
     * 자연검색 유입 검색어별 성과를 조회한다.
     *
     * @param dimension query(검색어) 또는 page(페이지)
     */
    List<SearchConsoleRow> query(LocalDate since, LocalDate until, String dimension, int limit);

    /** 연동이 실제로 가능한 상태인지 진단한다. */
    Health health();

    record SearchConsoleRow(
            String key,
            long impressions,
            long clicks,
            double ctr,
            double position
    ) {}

    record Health(String provider, boolean configured, boolean reachable, String message) {}
}
