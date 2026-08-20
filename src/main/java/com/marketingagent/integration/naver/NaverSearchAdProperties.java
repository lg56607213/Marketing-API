package com.marketingagent.integration.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("naver.searchad")
public record NaverSearchAdProperties(
        String provider,
        String baseUrl,
        String customerId,
        String apiKey,
        String secretKey,
        boolean syncEnabled,
        String syncCron,
        int syncDays
) {}
