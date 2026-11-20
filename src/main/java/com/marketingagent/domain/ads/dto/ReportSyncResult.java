package com.marketingagent.domain.ads.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 시간대별 성과·전환 리포트 수집 결과.
 *
 * @param failures 받지 못한 리포트. "2026-11-12 AD_DETAIL: 사유" 형태
 */
public record ReportSyncResult(
        int days,
        LocalDate since,
        LocalDate until,
        int hourlyRows,
        int conversionRows,
        List<String> failures
) {}
