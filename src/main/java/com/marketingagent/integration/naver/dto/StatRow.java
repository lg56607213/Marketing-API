package com.marketingagent.integration.naver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * GET /stats 응답의 일자별 성과 한 줄.
 * id 는 조회 대상(키워드/광고그룹/캠페인)의 ncc ID 이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatRow(
        String id,
        LocalDate statDate,
        long impCnt,
        long clkCnt,
        long salesAmt,
        double ctr,
        double cpc,
        double avgRnk,
        long ccnt
) {}
