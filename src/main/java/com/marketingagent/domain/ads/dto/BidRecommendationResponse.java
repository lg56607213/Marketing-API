package com.marketingagent.domain.ads.dto;

import com.marketingagent.domain.ads.BidRecommendation;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BidRecommendationResponse(
        Long id,
        String nccKeywordId,
        String keyword,
        long currentBid,
        long recommendedBid,
        long changeAmount,
        double changeRate,
        String direction,
        String reason,
        LocalDate since,
        LocalDate until,
        long impCnt,
        long clkCnt,
        long salesAmt,
        long ccnt,
        double ctr,
        double avgRnk,
        String strategy,
        String status,
        Long appliedBid,
        LocalDateTime decidedAt,
        String resultMessage
) {
    public static BidRecommendationResponse from(BidRecommendation r) {
        long change = r.changeAmount();
        double rate = r.getCurrentBid() == 0 ? 0.0
                : Math.round(change * 1000.0 / r.getCurrentBid()) / 10.0;
        return new BidRecommendationResponse(
                r.getId(), r.getNccKeywordId(), r.getKeyword(), r.getCurrentBid(), r.getRecommendedBid(),
                change, rate, change > 0 ? "UP" : "DOWN", r.getReason(), r.getSince(), r.getUntil(),
                r.getImpCnt(), r.getClkCnt(), r.getSalesAmt(), r.getCcnt(), r.getCtr(), r.getAvgRnk(),
                r.getStrategy().name(), r.getStatus().name(), r.getAppliedBid(), r.getDecidedAt(), r.getResultMessage());
    }
}
