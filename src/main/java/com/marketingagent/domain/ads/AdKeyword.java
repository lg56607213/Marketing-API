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
@Table(name = "ad_keywords",
        uniqueConstraints = @UniqueConstraint(name = "uk_ad_keywords_account",
                columnNames = {"ad_account_id", "ncc_keyword_id"}))
public class AdKeyword extends BaseEntity {

    @Column(name = "ad_account_id")
    private Long adAccountId;

    @Column(name = "ncc_keyword_id", nullable = false, length = 100)
    private String nccKeywordId;

    @Column(name = "ncc_adgroup_id", nullable = false, length = 100)
    private String nccAdgroupId;

    @Column(nullable = false)
    private String keyword;

    @Column(length = 50)
    private String status;

    private Long bidAmt;

    private Boolean useGroupBidAmt;

    public AdKeyword(Long adAccountId, String nccKeywordId, String nccAdgroupId, String keyword, String status,
            Long bidAmt, Boolean useGroupBidAmt) {
        this.adAccountId = adAccountId;
        this.nccKeywordId = nccKeywordId;
        this.nccAdgroupId = nccAdgroupId;
        this.keyword = keyword;
        this.status = status;
        this.bidAmt = bidAmt;
        this.useGroupBidAmt = useGroupBidAmt;
    }

    public void update(String keyword, String status, Long bidAmt, Boolean useGroupBidAmt) {
        this.keyword = keyword;
        this.status = status;
        this.bidAmt = bidAmt;
        this.useGroupBidAmt = useGroupBidAmt;
    }
}
