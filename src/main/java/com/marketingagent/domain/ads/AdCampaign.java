package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "ad_campaigns",
        uniqueConstraints = @UniqueConstraint(name = "uk_ad_campaigns_account",
                columnNames = {"ad_account_id", "ncc_campaign_id"}))
public class AdCampaign extends BaseEntity {

    @Column(name = "ad_account_id")
    private Long adAccountId;

    @Column(name = "ncc_campaign_id", nullable = false, length = 100)
    private String nccCampaignId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String campaignTp;

    @Column(length = 50)
    private String status;

    private Long dailyBudget;

    /**
     * 이 캠페인의 목표 전환당 비용. 사이트마다 전환 단가가 달라 캠페인별로 둔다.
     * null 또는 0이면 계정 기본값을 따른다. 동기화로 덮어쓰지 않는다.
     */
    private Long targetCpa;

    /** 전환 1건으로 남는 돈. 자동 운영의 클릭 1회 최대 금액 계산에 쓴다. null 이면 계정 기본값. 동기화로 덮어쓰지 않는다. */
    private Long breakEvenCpa;

    /** 클릭 1회 최대 금액. 있으면 손익분기 계산보다 먼저 쓴다. 동기화로 덮어쓰지 않는다. */
    private Long maxCpc;

    /** 키워드 판단에 쓰는 이 캠페인의 상품·고객 설명. 동기화로 덮어쓰지 않는다. */
    @Column(length = 500)
    private String businessNote;

    public AdCampaign(Long adAccountId, String nccCampaignId, String name, String campaignTp,
            String status, Long dailyBudget) {
        this.adAccountId = adAccountId;
        this.nccCampaignId = nccCampaignId;
        this.name = name;
        this.campaignTp = campaignTp;
        this.status = status;
        this.dailyBudget = dailyBudget;
    }

    public void update(String name, String campaignTp, String status, Long dailyBudget) {
        this.name = name;
        this.campaignTp = campaignTp;
        this.status = status;
        this.dailyBudget = dailyBudget;
    }

    /** 0 이하나 빈 값은 "계정 기본값을 따른다"로 저장한다. */
    public void changeEconomics(Long breakEvenCpa, Long maxCpc, String businessNote) {
        this.breakEvenCpa = breakEvenCpa != null && breakEvenCpa > 0 ? breakEvenCpa : null;
        this.maxCpc = maxCpc != null && maxCpc > 0 ? maxCpc : null;
        this.businessNote = businessNote == null || businessNote.isBlank() ? null
                : businessNote.strip().substring(0, Math.min(500, businessNote.strip().length()));
    }

    public void changeTargetCpa(Long targetCpa) {
        this.targetCpa = targetCpa != null && targetCpa > 0 ? targetCpa : null;
    }
}
