package com.marketingagent.domain.subsidy;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "subsidy_announcements")
public class SubsidyAnnouncement extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, length = 100)
    private String sourceId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 200)
    private String agency;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String targetText;

    @Column(length = 300)
    private String regionText;

    private LocalDate applyStart;

    private LocalDate applyEnd;

    @Column(length = 1000)
    private String url;

    @Lob
    @Column(name = "raw_json", columnDefinition = "MEDIUMTEXT")
    private String rawJson;

    @Column(nullable = false, length = 200)
    private String dedupeKey;

    @Lob
    @Column(name = "eligibility_json", columnDefinition = "TEXT")
    private String eligibilityJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    private LocalDateTime analyzedAt;

    /** 직접 등록한 업체. null 이면 모두에게 보이는 공고(수집·관리자 공용 등록). */
    private Long companyId;

    private Long createdBy;

    public SubsidyAnnouncement(String source, String sourceId) {
        this.source = source;
        this.sourceId = sourceId;
    }

    /** 수집한 내용으로 갱신한다. 본문이 바뀌면 다시 분석하도록 분석 시각을 비운다. */
    public void refresh(Normalized n, String dedupeKey) {
        boolean changed = this.title != null && (!Objects.equals(this.title, n.title())
                || !Objects.equals(this.summary, n.summary()) || !Objects.equals(this.targetText, n.targetText())
                || !Objects.equals(this.applyEnd, n.applyEnd()));
        this.title = n.title();
        this.agency = n.agency();
        this.summary = n.summary();
        this.targetText = n.targetText();
        this.regionText = n.regionText();
        this.applyStart = n.applyStart();
        this.applyEnd = n.applyEnd();
        this.url = n.url();
        this.rawJson = n.rawJson();
        this.dedupeKey = dedupeKey;
        if (changed) {
            this.analyzedAt = null;
        }
    }

    /** 직접 등록한 공고의 소유자. */
    public void ownedBy(Long companyId, Long userId) {
        this.companyId = companyId;
        this.createdBy = userId;
    }

    public boolean isManual() {
        return "manual".equals(source);
    }

    public void analyzed(String eligibilityJson, String aiSummary) {
        this.eligibilityJson = eligibilityJson;
        this.aiSummary = aiSummary;
        this.analyzedAt = LocalDateTime.now();
    }

    /** 수집기가 소스마다 다른 응답을 이 모양으로 맞춘다. */
    public record Normalized(String source, String sourceId, String title, String agency, String summary,
                             String targetText, String regionText, LocalDate applyStart, LocalDate applyEnd,
                             String url, String rawJson) {}
}
