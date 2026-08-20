package com.marketingagent.domain.ads.dto;

import com.marketingagent.domain.ads.BidRecommendation;

public record BidApplyResult(
        Long id,
        String keyword,
        String status,
        Long currentBid,
        Long appliedBid,
        String message
) {
    public static BidApplyResult of(BidRecommendation r) {
        return new BidApplyResult(r.getId(), r.getKeyword(), r.getStatus().name(),
                r.getCurrentBid(), r.getAppliedBid(), r.getResultMessage());
    }

    public static BidApplyResult failed(Long id, String message) {
        return new BidApplyResult(id, null, "FAILED", null, null, message);
    }
}
