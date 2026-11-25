package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import org.hibernate.annotations.ColumnDefault;

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

    /** 유입 검색어·목표 상담 키워드 중 업종에 맞는 것을 키워드로 추가한다. */
    @Column(name = "add_keywords", nullable = false)
    @ColumnDefault("1")
    private boolean addKeywords = true;

    /** 업종과 무관한 검색어를 제외 키워드로 막는다. */
    @Column(name = "add_negatives", nullable = false)
    @ColumnDefault("1")
    private boolean addNegatives = true;

    /** 자동 운영이 끄거나 추가한 키워드 중 30일 동안 실적이 없는 것을 지운다. */
    @Column(name = "cleanup_keywords", nullable = false)
    @ColumnDefault("1")
    private boolean cleanupKeywords = true;

    /** 낮 동안 1시간마다 목표 순위에 맞춰 입찰가를 조정한다. 전환 데이터가 없어도 동작한다. */
    @Column(name = "realtime_bids", nullable = false)
    @ColumnDefault("1")
    private boolean realtimeBids = true;

    /** 실시간 입찰이 노리는 평균 노출 순위 */
    @Column(name = "target_rank", nullable = false)
    @ColumnDefault("3")
    private int targetRank = 3;

    /** 클릭 1회 최대 금액. 0 이면 목표 상담의 손익분기로 자동 계산한다. */
    @Column(name = "max_cpc", nullable = false)
    @ColumnDefault("0")
    private long maxCpc;

    /** 이 날짜까지는 판단만 하고 네이버에는 반영하지 않는다. */
    @Column(name = "observe_until")
    private LocalDate observeUntil;

    public void updateDelegation(boolean addKeywords, boolean addNegatives, boolean cleanupKeywords,
            boolean realtimeBids, int targetRank, long maxCpc, LocalDate observeUntil) {
        this.addKeywords = addKeywords;
        this.addNegatives = addNegatives;
        this.cleanupKeywords = cleanupKeywords;
        this.realtimeBids = realtimeBids;
        this.targetRank = targetRank;
        this.maxCpc = maxCpc;
        this.observeUntil = observeUntil;
    }

    /** 지켜보기 기간이면 판단과 기록만 하고 네이버에는 반영하지 않는다. */
    public boolean isObserving(LocalDate today) {
        return observeUntil != null && !today.isAfter(observeUntil);
    }

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
