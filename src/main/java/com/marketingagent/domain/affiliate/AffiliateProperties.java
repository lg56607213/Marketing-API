package com.marketingagent.domain.affiliate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브랜드커넥트 상품 글 설정.
 *
 * @param naverClientId     네이버 개발자센터 검색 API Client ID. 비면 상품 정보를 사용자 입력만으로 쓴다
 * @param naverClientSecret 네이버 개발자센터 검색 API Client Secret
 * @param shopUrl           네이버 쇼핑 검색 API 주소
 * @param writeModel        글 쓰는 모델
 * @param maxPerDay         업체당 하루 글 생성 상한(AI 비용 상한)
 */
@ConfigurationProperties(prefix = "affiliate")
public record AffiliateProperties(
        String naverClientId,
        String naverClientSecret,
        String shopUrl,
        String writeModel,
        int maxPerDay) {

    public boolean shopConfigured() {
        return naverClientId != null && !naverClientId.isBlank() && naverClientSecret != null
                && !naverClientSecret.isBlank();
    }
}
