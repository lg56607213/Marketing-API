package com.marketingagent.domain.ads.dto;

/**
 * 검색어별 성과.
 *
 * @param registered 이 검색어가 등록 키워드와 정확히 일치하는지. false 면 확장검색으로 들어온 것이다.
 */
public record SearchQueryPerformance(
        String searchQuery,
        boolean registered,
        long impCnt,
        long clkCnt,
        double ctr,
        double cpc,
        long salesAmt
) {}
