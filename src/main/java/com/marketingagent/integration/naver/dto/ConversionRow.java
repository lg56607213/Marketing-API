package com.marketingagent.integration.naver.dto;

import java.time.LocalDate;

/**
 * 전환 상세 리포트(AD_CONVERSION_DETAIL)의 한 줄.
 *
 * @param hour       0~23
 * @param device     P(PC) 또는 M(모바일)
 * @param convMethod 1 직접전환, 2 간접전환
 * @param convType   purchase, sign_up, add_to_cart, lead, custom001 등
 * @param convAmt    전환매출액
 */
public record ConversionRow(
        LocalDate statDate,
        int hour,
        String nccCampaignId,
        String nccAdgroupId,
        String nccKeywordId,
        String device,
        int convMethod,
        String convType,
        long convCnt,
        long convAmt
) {}
