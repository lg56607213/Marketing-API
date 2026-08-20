package com.marketingagent.domain.ads.dto;

/** JPQL 집계 결과. 비율 지표는 합계에서 다시 계산하므로 여기서는 원시 합계만 담는다. */
public record KeywordAggregate(
        String nccKeywordId,
        Long impCnt,
        Long clkCnt,
        Long salesAmt,
        Long ccnt,
        Double avgRnk
) {}
