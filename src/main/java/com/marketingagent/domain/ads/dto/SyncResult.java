package com.marketingagent.domain.ads.dto;

import java.time.LocalDate;

public record SyncResult(
        String provider,
        int campaigns,
        int adgroups,
        int keywords,
        int statRows,
        LocalDate since,
        LocalDate until
) {}
