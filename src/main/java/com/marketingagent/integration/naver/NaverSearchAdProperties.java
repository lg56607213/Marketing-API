package com.marketingagent.integration.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param syncDays       일 배치에서 계정 구조·일자별 성과를 받을 일수
 * @param reportSyncDays 일 배치에서 시간대별 성과·전환·검색어 리포트를 다시 받을 일수.
 *                       간접전환이 뒤늦게 잡히므로 최근 며칠은 매일 다시 받는다.
 */
@ConfigurationProperties("naver.searchad")
public record NaverSearchAdProperties(
        String provider,
        String baseUrl,
        String customerId,
        String apiKey,
        String secretKey,
        boolean syncEnabled,
        String syncCron,
        int syncDays,
        int reportSyncDays
) {}
