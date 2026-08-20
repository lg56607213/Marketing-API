package com.marketingagent.domain.ads;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 입찰가 조정 추천 1건. 생성 시점의 근거와 반영 결과를 함께 남겨 감사 추적이 가능하게 한다.
 * 사용자가 승인하기 전에는 절대 네이버에 반영하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "bid_recommendations")
public class BidRecommendation extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String nccKeywordId;

    @Column(nullable = false)
    private String keyword;

    /** 추천 생성 시점의 입찰가 */
    @Column(nullable = false)
    private long currentBid;

    /** 가드레일까지 적용한 최종 추천 입찰가 */
    @Column(nullable = false)
    private long recommendedBid;

    /** 추천 근거 (사람이 읽는 문장) */
    @Column(nullable = false, length = 500)
    private String reason;

    /** 근거가 된 분석 구간 */
    @Column(name = "analysis_since", nullable = false)
    private LocalDate since;

    @Column(name = "analysis_until", nullable = false)
    private LocalDate until;

    private long impCnt;
    private long clkCnt;
    private long salesAmt;
    private long ccnt;
    private double ctr;
    private double avgRnk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BidStatus status;

    /** 실제로 반영된 입찰가. 반영 전에는 null */
    private Long appliedBid;

    private LocalDateTime decidedAt;

    private Long decidedBy;

    @Column(length = 500)
    private String resultMessage;

    public BidRecommendation(String nccKeywordId, String keyword, long currentBid, long recommendedBid,
            String reason, LocalDate since, LocalDate until, long impCnt, long clkCnt, long salesAmt,
            long ccnt, double ctr, double avgRnk) {
        this.nccKeywordId = nccKeywordId;
        this.keyword = keyword;
        this.currentBid = currentBid;
        this.recommendedBid = recommendedBid;
        this.reason = reason;
        this.since = since;
        this.until = until;
        this.impCnt = impCnt;
        this.clkCnt = clkCnt;
        this.salesAmt = salesAmt;
        this.ccnt = ccnt;
        this.ctr = ctr;
        this.avgRnk = avgRnk;
        this.status = BidStatus.PENDING;
    }

    public void markApplied(long appliedBid, Long actorId, String message) {
        this.status = BidStatus.APPLIED;
        this.appliedBid = appliedBid;
        this.decidedBy = actorId;
        this.decidedAt = LocalDateTime.now();
        this.resultMessage = message;
    }

    public void markFailed(Long actorId, String message) {
        this.status = BidStatus.FAILED;
        this.decidedBy = actorId;
        this.decidedAt = LocalDateTime.now();
        this.resultMessage = message;
    }

    public void markRejected(Long actorId) {
        this.status = BidStatus.REJECTED;
        this.decidedBy = actorId;
        this.decidedAt = LocalDateTime.now();
    }

    public void markSuperseded() {
        this.status = BidStatus.SUPERSEDED;
        this.decidedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == BidStatus.PENDING;
    }

    public long changeAmount() {
        return recommendedBid - currentBid;
    }
}
