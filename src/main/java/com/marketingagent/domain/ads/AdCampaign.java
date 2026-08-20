package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "ad_campaigns")
public class AdCampaign extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
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
