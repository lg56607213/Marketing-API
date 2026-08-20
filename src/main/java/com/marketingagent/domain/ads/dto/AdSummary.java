package com.marketingagent.domain.ads.dto;

import java.time.LocalDate;
import java.util.List;

public record AdSummary(
        LocalDate since,
        LocalDate until,
        long impCnt,
        long clkCnt,
        double ctr,
        double cpc,
        long salesAmt,
        long ccnt,
        double cvr,
        double cpa,
        int keywordCount,
        List<DailyPoint> daily
) {}
