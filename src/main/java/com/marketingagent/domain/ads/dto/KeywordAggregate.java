package com.marketingagent.domain.ads.dto;

/**
 * JPQL 집계 결과. 비율 지표는 합계에서 다시 계산하므로 여기서는 원시 합계만 담는다.
 *
 * @param rnkWeighted 평균순위 x 노출수의 합. 노출수로 나누어 노출가중 평균순위를 구한다.
 */
public record KeywordAggregate(
        String nccKeywordId,
        Long impCnt,
        Long clkCnt,
        Long salesAmt,
        Long ccnt,
        Double rnkWeighted
) {}
