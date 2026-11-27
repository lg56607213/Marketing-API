package com.marketingagent.domain.subsidy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SubsidyDtos {

    private SubsidyDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileRequest(String name, String entityType, LocalDate foundedDate, String regionSido,
                                 String regionSigungu, String ksicCode, String industryName, Long revenueKrw,
                                 Integer employees, List<String> certifications, Integer ceoBirthYear,
                                 String ceoGender, String keywords, Boolean emailOptIn, String recipients) {}

    public record ProfileView(Long id, String name, String entityType, LocalDate foundedDate, String regionSido,
                              String regionSigungu, String ksicCode, String industryName, Long revenueKrw,
                              Integer employees, List<String> certifications, Integer ceoBirthYear, String ceoGender,
                              String keywords, boolean emailOptIn, String recipients) {}

    /** 추천 공고 한 줄. 금액·유형·요약은 공고 분석 결과. */
    public record MatchView(Long id, Long announcementId, String title, String agency, String url, String source,
                            LocalDate applyStart, LocalDate applyEnd, Long daysLeft, String supportType,
                            String amountText, String summary, int score, String verdict, List<String> reasons,
                            List<String> blockers, boolean aiJudged, boolean starred) {}

    public record JobRunView(Long id, String job, LocalDateTime startedAt, LocalDateTime finishedAt, String status,
                             String detail) {}

    public record Status(boolean enabled, boolean bizinfoConfigured, boolean kstartupConfigured, boolean aiAvailable,
                         long announcements, long unanalyzed, long profiles, List<JobRunView> recentRuns) {}
}
