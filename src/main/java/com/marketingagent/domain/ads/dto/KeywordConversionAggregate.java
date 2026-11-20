package com.marketingagent.domain.ads.dto;

/** JPQL 집계 결과: 키워드·전환방식·전환유형별 전환 합계. */
public record KeywordConversionAggregate(
        String nccKeywordId,
        Integer convMethod,
        String convType,
        Long convCnt,
        Long convAmt
) {}
