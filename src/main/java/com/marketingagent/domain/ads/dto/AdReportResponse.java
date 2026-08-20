package com.marketingagent.domain.ads.dto;

import com.marketingagent.domain.ads.AdReport;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdReportResponse(
        Long id,
        String title,
        String body,
        LocalDate since,
        LocalDate until,
        String generatedBy,
        LocalDateTime createdAt
) {
    public static AdReportResponse from(AdReport r) {
        return new AdReportResponse(r.getId(), r.getTitle(), r.getBody(),
                r.getSince(), r.getUntil(), r.getGeneratedBy(), r.getCreatedAt());
    }
}
