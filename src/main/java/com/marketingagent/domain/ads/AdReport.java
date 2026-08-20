package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기간별 광고 성과 분석 리포트. 무엇이 생성했는지(AI/규칙)까지 남긴다. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "ad_reports")
public class AdReport extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "analysis_since", nullable = false)
    private LocalDate since;

    @Column(name = "analysis_until", nullable = false)
    private LocalDate until;

    /** "openai:gpt-4.1-mini" 또는 "rule-based" */
    @Column(nullable = false, length = 100)
    private String generatedBy;

    public AdReport(String title, String body, LocalDate since, LocalDate until, String generatedBy) {
        this.title = title;
        this.body = body;
        this.since = since;
        this.until = until;
        this.generatedBy = generatedBy;
    }
}
