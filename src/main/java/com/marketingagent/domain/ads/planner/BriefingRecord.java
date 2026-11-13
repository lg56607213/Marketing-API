package com.marketingagent.domain.ads.planner;

import com.marketingagent.common.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저장된 일간·주간 브리핑. 같은 기간을 다시 만들면 덮어쓴다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "briefings",
        uniqueConstraints = @UniqueConstraint(name = "uk_briefings",
                columnNames = {"owner_id", "period", "until_date"}))
public class BriefingRecord extends OwnedEntity {

    @Column(nullable = false, length = 10)
    private String period;

    @Column(name = "since_date", nullable = false)
    private LocalDate since;

    @Column(name = "until_date", nullable = false)
    private LocalDate until;

    @Column(nullable = false, length = 20)
    private String verdict;

    @Column(nullable = false, length = 300)
    private String headline;

    /** 브리핑 전체(Briefing)를 JSON 으로 보관한다. */
    @Lob
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    private String contentJson;

    public BriefingRecord(String period, LocalDate since, LocalDate until, String verdict, String headline,
            String contentJson) {
        this.period = period;
        this.since = since;
        this.until = until;
        update(verdict, headline, contentJson);
    }

    public void update(String verdict, String headline, String contentJson) {
        this.verdict = verdict;
        this.headline = headline;
        this.contentJson = contentJson;
    }
}
