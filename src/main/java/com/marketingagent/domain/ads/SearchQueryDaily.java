package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 광고가 실제로 노출된 검색어의 일자별 성과.
 * 등록 키워드뿐 아니라 확장검색으로 매칭된 검색어까지 담긴다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "search_query_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_search_query_daily",
                columnNames = {"stat_date", "ncc_adgroup_id", "search_query", "device"}))
public class SearchQueryDaily extends BaseEntity {

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "ncc_campaign_id", nullable = false, length = 100)
    private String nccCampaignId;

    @Column(name = "ncc_adgroup_id", nullable = false, length = 100)
    private String nccAdgroupId;

    @Column(name = "search_query", nullable = false, length = 255)
    private String searchQuery;

    /** P(PC) 또는 M(모바일) */
    @Column(nullable = false, length = 10)
    private String device;

    private long impCnt;

    private long clkCnt;

    private long salesAmt;

    public SearchQueryDaily(LocalDate statDate, String nccCampaignId, String nccAdgroupId,
            String searchQuery, String device, long impCnt, long clkCnt, long salesAmt) {
        this.statDate = statDate;
        this.nccCampaignId = nccCampaignId;
        this.nccAdgroupId = nccAdgroupId;
        this.searchQuery = searchQuery;
        this.device = device;
        this.impCnt = impCnt;
        this.clkCnt = clkCnt;
        this.salesAmt = salesAmt;
    }

    public void update(long impCnt, long clkCnt, long salesAmt) {
        this.impCnt = impCnt;
        this.clkCnt = clkCnt;
        this.salesAmt = salesAmt;
    }
}
