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
 * 키워드 단위 일자별 성과. (키워드, 일자) 조합으로 유일하며 재수집 시 덮어쓴다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "keyword_stat_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_keyword_stat_daily",
                columnNames = {"ncc_keyword_id", "stat_date"}))
public class KeywordStatDaily extends BaseEntity {

    @Column(name = "ncc_keyword_id", nullable = false, length = 100)
    private String nccKeywordId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    /** 노출수 */
    private long impCnt;

    /** 클릭수 */
    private long clkCnt;

    /** 광고비 */
    private long salesAmt;

    /** 클릭률(%) */
    private double ctr;

    /** 평균 클릭비용 */
    private double cpc;

    /** 평균 노출순위 */
    private double avgRnk;

    /** 전환수 */
    private long ccnt;

    public KeywordStatDaily(String nccKeywordId, LocalDate statDate, long impCnt, long clkCnt,
            long salesAmt, double ctr, double cpc, double avgRnk, long ccnt) {
        this.nccKeywordId = nccKeywordId;
        this.statDate = statDate;
        this.impCnt = impCnt;
        this.clkCnt = clkCnt;
        this.salesAmt = salesAmt;
        this.ctr = ctr;
        this.cpc = cpc;
        this.avgRnk = avgRnk;
        this.ccnt = ccnt;
    }

    public void update(long impCnt, long clkCnt, long salesAmt, double ctr, double cpc,
            double avgRnk, long ccnt) {
        this.impCnt = impCnt;
        this.clkCnt = clkCnt;
        this.salesAmt = salesAmt;
        this.ctr = ctr;
        this.cpc = cpc;
        this.avgRnk = avgRnk;
        this.ccnt = ccnt;
    }
}
