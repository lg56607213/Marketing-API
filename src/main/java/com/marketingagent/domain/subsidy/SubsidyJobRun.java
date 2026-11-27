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
@Table(name = "subsidy_job_runs")
public class SubsidyJobRun extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String job;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** RUNNING | OK | PARTIAL | FAILED | SKIPPED */
    @Column(nullable = false, length = 20)
    private String status;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String detail;

    public SubsidyJobRun(String job) {
        this.job = job;
        this.startedAt = LocalDateTime.now();
        this.status = "RUNNING";
    }

    public void finish(String status, String detail) {
        this.status = status;
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 60000));
        this.finishedAt = LocalDateTime.now();
    }
}
