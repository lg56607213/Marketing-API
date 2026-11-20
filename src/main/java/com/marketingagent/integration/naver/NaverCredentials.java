package com.marketingagent.integration.naver;

/**
 * 광고 계정 하나를 호출하는 데 필요한 값. 계정마다 따로 만든다.
 * 로그에 비밀값이 남지 않도록 toString 을 가린다.
 */
public record NaverCredentials(String baseUrl, String customerId, String apiKey, String secretKey) {

    @Override
    public String toString() {
        return "NaverCredentials[customerId=" + customerId + "]";
    }
}
