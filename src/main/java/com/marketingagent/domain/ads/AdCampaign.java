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

    public void changeTargetCpa(Long targetCpa) {
        this.targetCpa = targetCpa != null && targetCpa > 0 ? targetCpa : null;
    }
}
