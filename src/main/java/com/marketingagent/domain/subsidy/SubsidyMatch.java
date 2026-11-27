package com.marketingagent.domain.subsidy;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "subsidy_matches")
public class SubsidyMatch extends BaseEntity {

    public static final String FIT = "fit";
    public static final String MAYBE = "maybe";
    public static final String NO = "no";

    @Column(nullable = false)
    private Long profileId;

    @Column(nullable = false)
    private Long announcementId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 10)
    private String verdict;

    /** 줄바꿈으로 구분 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String reasons;

    /** 줄바꿈으로 구분 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String blockers;

    @Column(nullable = false)
    private boolean aiJudged;

    @Column(nullable = false)
    private boolean starred;

    private LocalDateTime notifiedAt;

    private LocalDateTime deadlineNotifiedAt;

    public SubsidyMatch(Long profileId, Long announcementId) {
        this.profileId = profileId;
        this.announcementId = announcementId;
    }

    public void judged(int score, String verdict, String reasons, String blockers, boolean aiJudged) {
        this.score = score;
        this.verdict = verdict;
        this.reasons = reasons;
        this.blockers = blockers;
        this.aiJudged = aiJudged;
    }

    public void star(boolean starred) {
        this.starred = starred;
    }

    public void notified() {
        this.notifiedAt = LocalDateTime.now();
    }

    public void deadlineNotified() {
        this.deadlineNotifiedAt = LocalDateTime.now();
    }
}
