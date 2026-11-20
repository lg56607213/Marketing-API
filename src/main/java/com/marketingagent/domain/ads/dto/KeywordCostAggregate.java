package com.marketingagent.domain.ads.dto;

/** JPQL 집계 결과: 키워드별 노출·클릭·비용 합계. */
public record KeywordCostAggregate(
        String nccKeywordId,
        Long impCnt,
        Long clkCnt,
        Long salesAmt
) {}
