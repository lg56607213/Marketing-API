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
@Table(name = "ad_keywords")
public class AdKeyword extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String nccKeywordId;

    @Column(nullable = false, length = 100)
    private String nccAdgroupId;

    @Column(nullable = false)
    private String keyword;

    @Column(length = 50)
    private String status;

    private Long bidAmt;

    private Boolean useGroupBidAmt;

    public AdKeyword(String nccKeywordId, String nccAdgroupId, String keyword, String status,
            Long bidAmt, Boolean useGroupBidAmt) {
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
