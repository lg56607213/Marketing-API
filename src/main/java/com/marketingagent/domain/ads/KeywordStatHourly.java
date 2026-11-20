package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 키워드 단위 시간대별 성과. (일자, 시간, 광고그룹, 키워드, 기기) 조합으로 유일하다.
 * 재수집 시 그 일자를 통째로 지우고 다시 넣는다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "keyword_stat_hourly",
        uniqueConstraints = @UniqueConstraint(name = "uk_keyword_stat_hourly",
                columnNames = {"ad_account_id", "stat_date", "stat_hour", "ncc_adgroup_id", "ncc_keyword_id", "device"}),
        indexes = @Index(name = "ix_keyword_stat_hourly_keyword", columnList = "ncc_keyword_id, stat_date"))
public class KeywordStatHourly extends BaseEntity {

    /** 어느 광고 계정의 데이터인지. 업체마다, 계정마다 따로 쌓인다. */
    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;


    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    /** 0~23 */
    @Column(name = "stat_hour", nullable = false)
    private int statHour;

    @Column(name = "ncc_campaign_id", nullable = false, length = 100)
    private String nccCampaignId;

    @Column(name = "ncc_adgroup_id", nullable = false, length = 100)
    private String nccAdgroupId;

    @Column(name = "ncc_keyword_id", nullable = false, length = 100)
    private String nccKeywordId;

    /** P(PC) 또는 M(모바일) */
    @Column(nullable = false, length = 10)
    private String device;

    private long impCnt;

    private long clkCnt;

    /** 광고비 */
    private long salesAmt;

    /** 노출순위 합. 평균순위 = rnkSum / impCnt */
    private long rnkSum;

    public KeywordStatHourly(Long adAccountId, LocalDate statDate, int statHour, String nccCampaignId, String nccAdgroupId,
            String nccKeywordId, String device, long impCnt, long clkCnt, long salesAmt, long rnkSum) {
        this.adAccountId = adAccountId;
        this.statDate = statDate;
        this.statHour = statHour;
        this.nccCampaignId = nccCampaignId;
        this.nccAdgroupId = nccAdgroupId;
        this.nccKeywordId = nccKeywordId;
        this.device = device;
        this.impCnt = impCnt;
        this.clkCnt = clkCnt;
        this.salesAmt = salesAmt;
        this.rnkSum = rnkSum;
    }
}
