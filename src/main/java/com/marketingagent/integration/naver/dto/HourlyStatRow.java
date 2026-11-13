package com.marketingagent.integration.naver.dto;

import java.time.LocalDate;

/**
 * 광고성과 상세 리포트(AD_DETAIL)의 한 줄.
 *
 * @param hour     0~23
 * @param device   P(PC) 또는 M(모바일)
 * @param salesAmt 광고비
 * @param rnkSum   노출순위 합. 평균순위 = rnkSum / impCnt
 */
public record HourlyStatRow(
        LocalDate statDate,
        int hour,
        String nccCampaignId,
        String nccAdgroupId,
        String nccKeywordId,
        String device,
        long impCnt,
        long clkCnt,
        long salesAmt,
        long rnkSum
) {}
