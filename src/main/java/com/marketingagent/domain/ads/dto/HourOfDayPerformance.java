package com.marketingagent.domain.ads.dto;

/**
 * 하루 중 시간대별 성과. 기간 안의 같은 시간대를 모두 합친 값이다.
 *
 * @param salesAmt 광고비
 * @param convAmt  전환매출액
 * @param cpa      전환당 비용. 전환이 없으면 0
 * @param roas     광고수익률(%) = 전환매출액 / 광고비 x 100
 */
public record HourOfDayPerformance(
        int hour,
        long impCnt,
        long clkCnt,
        long salesAmt,
        double ctr,
        double cpc,
        double avgRnk,
        long convCnt,
        long convAmt,
        double cpa,
        double roas
) {}
