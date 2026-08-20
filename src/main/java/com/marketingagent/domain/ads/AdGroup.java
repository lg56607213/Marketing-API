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
@Table(name = "ad_groups")
public class AdGroup extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String nccAdgroupId;

    @Column(nullable = false, length = 100)
    private String nccCampaignId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String status;

    private Long bidAmt;

    private Long dailyBudget;

    public AdGroup(String nccAdgroupId, String nccCampaignId, String name, String status,
            Long bidAmt, Long dailyBudget) {
        this.nccAdgroupId = nccAdgroupId;
        this.nccCampaignId = nccCampaignId;
        this.name = name;
        this.status = status;
        this.bidAmt = bidAmt;
        this.dailyBudget = dailyBudget;
    }

    public void update(String name, String status, Long bidAmt, Long dailyBudget) {
        this.name = name;
        this.status = status;
        this.bidAmt = bidAmt;
        this.dailyBudget = dailyBudget;
    }
}
