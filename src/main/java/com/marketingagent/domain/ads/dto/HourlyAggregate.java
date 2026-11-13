package com.marketingagent.domain.ads.dto;

/** JPQL 집계 결과: 시간대·기기별 성과 합계. */
public record HourlyAggregate(
        Integer hour,
        String device,
        Long impCnt,
        Long clkCnt,
        Long salesAmt,
        Long rnkSum
) {}
