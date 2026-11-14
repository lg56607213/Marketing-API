package com.marketingagent.integration.naver.dto;

/**
 * 네이버 성과 예측(/estimate/performance-bulk) 한 건. 이 입찰가로 걸었을 때 예상되는 월간 노출·클릭·비용이다.
 *
 * @param bid 예측에 쓴 입찰가
 */
public record PerformanceEstimate(
        String keyword,
        long bid,
        long impressions,
        long clicks,
        long cost
) {
    /** 예측에 쓸 (키워드, 입찰가) 한 쌍. */
    public record Query(String keyword, long bid) {}
}
