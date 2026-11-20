package com.marketingagent.domain.ads.dto;

/** JPQL 집계 결과: 시간대·기기별 전환 합계. */
public record HourlyConversionAggregate(
        Integer hour,
        String device,
        Long convCnt,
        Long convAmt
) {}
