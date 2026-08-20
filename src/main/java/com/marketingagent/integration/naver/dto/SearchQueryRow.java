package com.marketingagent.integration.naver.dto;

import java.time.LocalDate;

/**
 * 검색어 리포트(EXPKEYWORD)의 한 줄. 광고가 실제로 어떤 검색어에 노출됐는지 보여준다.
 *
 * @param device P(PC) 또는 M(모바일)
 */
public record SearchQueryRow(
        LocalDate statDate,
        String nccCampaignId,
        String nccAdgroupId,
        String searchQuery,
        String device,
        long impCnt,
        long clkCnt,
        long salesAmt
) {}
