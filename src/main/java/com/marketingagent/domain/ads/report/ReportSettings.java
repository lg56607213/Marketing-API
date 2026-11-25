package com.marketingagent.domain.ads.report;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 광고 계정별 매일 보고 메일 설정. 받는 사람은 업체가 화면에서 정한다. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "report_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_settings_account", columnNames = "ad_account_id"))
public class ReportSettings extends BaseEntity {

    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 받는 사람. 쉼표로 구분 */
    @Column(nullable = false, length = 1000)
    private String recipients = "";

    /** 마지막으로 보낸 날. 같은 날 두 번 보내지 않으려고 둔다 */
    @Column(name = "last_sent_on")
    private LocalDate lastSentOn;

    public ReportSettings(Long adAccountId) {
        this.adAccountId = adAccountId;
    }

    public void update(boolean enabled, List<String> recipients) {
        this.enabled = enabled;
        this.recipients = String.join(",", recipients);
    }

    public void markSent(LocalDate day) {
        this.lastSentOn = day;
    }

    public List<String> recipientList() {
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
