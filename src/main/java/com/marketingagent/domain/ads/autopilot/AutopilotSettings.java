package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 자동 운영 설정. 기본은 꺼져 있고, 회원이 직접 켜야만 시스템이 광고 계정을 바꾼다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "autopilot_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_autopilot_settings_account", columnNames = "ad_account_id"))
public class AutopilotSettings extends BaseEntity {

    /** 어느 광고 계정의 데이터인지. 업체마다, 계정마다 따로 쌓인다. */
    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;

    public AutopilotSettings(Long adAccountId) {
        this.adAccountId = adAccountId;
    }


    @Column(nullable = false)
    private boolean enabled;

    /** 목표 전환당 비용. 0 이면 운영 계획의 목표를 따른다. */
    @Column(name = "target_cpa", nullable = false)
    private long targetCpa;

    /** 목표 광고수익률(%). 0 이면 쓰지 않는다. */
    @Column(name = "target_roas_pct", nullable = false)
    private double targetRoasPct;

    /** 한 달 광고비 한도. 0 이면 운영 계획의 월 예산을 따른다. */
    @Column(name = "monthly_budget_cap", nullable = false)
    private long monthlyBudgetCap;

    @Column(name = "manage_bids", nullable = false)
    private boolean manageBids = true;

    @Column(name = "manage_budget", nullable = false)
    private boolean manageBudget = true;

    @Column(name = "pause_wasteful", nullable = false)
    private boolean pauseWasteful = true;

    public void update(boolean enabled, long targetCpa, double targetRoasPct, long monthlyBudgetCap,
            boolean manageBids, boolean manageBudget, boolean pauseWasteful) {
        this.enabled = enabled;
        this.targetCpa = targetCpa;
        this.targetRoasPct = targetRoasPct;
        this.monthlyBudgetCap = monthlyBudgetCap;
        this.manageBids = manageBids;
        this.manageBudget = manageBudget;
        this.pauseWasteful = pauseWasteful;
    }
}
