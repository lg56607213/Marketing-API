package com.marketingagent.domain.ads;

import com.marketingagent.common.OwnedEntity;
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
        uniqueConstraints = @UniqueConstraint(name = "uk_ad_campaigns_owner",
                columnNames = {"owner_id", "ncc_campaign_id"}))
public class AdCampaign extends OwnedEntity {

    @Column(name = "ncc_campaign_id", nullable = false, length = 100)
    private String nccCampaignId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String campaignTp;

    @Column(length = 50)
    private String status;

    private Long dailyBudget;

    public AdCampaign(String nccCampaignId, String name, String campaignTp, String status, Long dailyBudget) {
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
}
