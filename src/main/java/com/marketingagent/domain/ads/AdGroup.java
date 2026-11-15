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
@Table(name = "ad_groups",
        uniqueConstraints = @UniqueConstraint(name = "uk_ad_groups_account",
                columnNames = {"ad_account_id", "ncc_adgroup_id"}))
public class AdGroup extends BaseEntity {

    @Column(name = "ad_account_id")
    private Long adAccountId;

    @Column(name = "ncc_adgroup_id", nullable = false, length = 100)
    private String nccAdgroupId;

    @Column(name = "ncc_campaign_id", nullable = false, length = 100)
    private String nccCampaignId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String status;

    private Long bidAmt;

    private Long dailyBudget;

    public AdGroup(Long adAccountId, String nccAdgroupId, String nccCampaignId, String name, String status,
            Long bidAmt, Long dailyBudget) {
        this.adAccountId = adAccountId;
        this.nccAdgroupId = nccAdgroupId;
        this.nccCampaignId = nccCampaignId;
        this.name = name;
        this.status = status;
        this.bidAmt = bidAmt;
        this.dailyBudget = dailyBudget;
    }

    public void update(String nccCampaignId, String name, String status, Long bidAmt, Long dailyBudget) {
        this.nccCampaignId = nccCampaignId;
        this.name = name;
        this.status = status;
        this.bidAmt = bidAmt;
        this.dailyBudget = dailyBudget;
    }
}
