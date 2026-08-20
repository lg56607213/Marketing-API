package com.marketingagent.integration.naver.dto;

/**
 * 키워드도구가 돌려주는 연관 키워드 한 건.
 *
 * @param keyword     연관 키워드
 * @param pcCount     월간 PC 검색수
 * @param mobileCount 월간 모바일 검색수
 * @param pcClick     월평균 PC 클릭수
 * @param mobileClick 월평균 모바일 클릭수
 * @param competition 경쟁 정도 (높음/중간/낮음)
 */
public record RelatedKeyword(
        String keyword,
        long pcCount,
        long mobileCount,
        double pcClick,
        double mobileClick,
        String competition
) {
    public long totalCount() {
        return pcCount + mobileCount;
    }
}
