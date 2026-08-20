package com.marketingagent.domain.ads.dto;

/** 검색어별 기간 합계. 비율 지표는 서비스에서 다시 계산한다. */
public record SearchQueryAggregate(
        String searchQuery,
        Long impCnt,
        Long clkCnt,
        Long salesAmt
) {}
