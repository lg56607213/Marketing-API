package com.marketingagent.domain.ads.dto;

/**
 * 검색광고 연동 상태 자가진단 결과. 실계정 키를 처음 붙일 때 이걸로 확인한다.
 *
 * @param provider      현재 사용 중인 클라이언트 (stub / naver)
 * @param credentialsSet 자격증명 3종이 모두 채워졌는지
 * @param reachable     실제 API 호출이 성공했는지
 * @param campaignCount 조회된 캠페인 수
 * @param message       사람이 읽는 진단 메시지
 */
public record AdHealth(
        String provider,
        boolean credentialsSet,
        boolean reachable,
        int campaignCount,
        String message
) {}
