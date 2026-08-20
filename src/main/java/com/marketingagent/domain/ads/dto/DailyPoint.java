package com.marketingagent.domain.ads.dto;

import java.time.LocalDate;

public record DailyPoint(
        LocalDate statDate,
        Long impCnt,
        Long clkCnt,
        Long salesAmt,
        Long ccnt
) {}
