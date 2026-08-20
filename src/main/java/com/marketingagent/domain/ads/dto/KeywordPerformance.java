package com.marketingagent.domain.ads.dto;

public record KeywordPerformance(
        String nccKeywordId,
        String keyword,
        String adgroupName,
        String status,
        Long bidAmt,
        long impCnt,
        long clkCnt,
        double ctr,
        double cpc,
        long salesAmt,
        long ccnt,
        double cvr,
        double avgRnk
) {}
