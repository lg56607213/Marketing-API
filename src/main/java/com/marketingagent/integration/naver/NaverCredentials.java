package com.marketingagent.integration.naver;

/** 네이버 검색광고 API 호출에 쓰는 한 회원의 자격증명. 로그에 남기지 않도록 toString 을 가린다. */
public record NaverCredentials(String customerId, String apiKey, String secretKey) {

    @Override
    public String toString() {
        return "NaverCredentials[customerId=" + customerId + "]";
    }
}
