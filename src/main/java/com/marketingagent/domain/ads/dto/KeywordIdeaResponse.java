package com.marketingagent.domain.ads.dto;

import com.marketingagent.integration.naver.dto.RelatedKeyword;

/**
 * 키워드 발굴 결과 한 건.
 *
 * @param registered 이미 광고에 등록된 키워드인지
 */
public record KeywordIdeaResponse(
        String keyword,
        long pcCount,
        long mobileCount,
        long totalCount,
        double pcClick,
        double mobileClick,
        String competition,
        boolean registered
) {
    public static KeywordIdeaResponse from(RelatedKeyword r, boolean registered) {
        return new KeywordIdeaResponse(r.keyword(), r.pcCount(), r.mobileCount(), r.totalCount(),
                r.pcClick(), r.mobileClick(), r.competition(), registered);
    }
}
