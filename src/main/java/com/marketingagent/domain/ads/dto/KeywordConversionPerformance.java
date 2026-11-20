package com.marketingagent.domain.ads.dto;

/**
 * 키워드별 비용 대비 전환 성과.
 *
 * @param keyword       키워드 문자열. 계정 구조에 없는 ID면 null
 * @param salesAmt      광고비
 * @param convCnt       전체 전환수(직접 + 간접, 모든 유형)
 * @param directConvCnt 직접전환수
 * @param purchaseCnt   구매 전환수
 * @param convAmt       전환매출액
 * @param cpa           전환당 비용. 전환이 없으면 0
 * @param roas          광고수익률(%) = 전환매출액 / 광고비 x 100
 */
public record KeywordConversionPerformance(
        String nccKeywordId,
        String keyword,
        long impCnt,
        long clkCnt,
        long salesAmt,
        long convCnt,
        long directConvCnt,
        long purchaseCnt,
        long convAmt,
        double cpa,
        double roas
) {}
