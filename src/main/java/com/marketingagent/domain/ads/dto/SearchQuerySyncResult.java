package com.marketingagent.domain.ads.dto;

import java.time.LocalDate;

public record SearchQuerySyncResult(int days, int rows, LocalDate since, LocalDate until) {}
