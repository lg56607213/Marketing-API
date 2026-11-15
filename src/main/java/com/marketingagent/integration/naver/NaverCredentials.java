package com.marketingagent.integration.naver;

/** 광고 계정 하나를 호출하는 데 필요한 값. 계정마다 따로 만든다. */
public record NaverCredentials(String baseUrl, String customerId, String apiKey, String secretKey) {}
