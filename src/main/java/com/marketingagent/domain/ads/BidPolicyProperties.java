package com.marketingagent.domain.ads;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 입찰가 조정 가드레일. 자동 반영은 없으며, 승인된 건에 대해서도 여기 한도를 넘길 수 없다.
 *
 * @param targetCpa       목표 전환당 비용. 0이면 계정 평균 CPA를 기준으로 삼는다.
 * @param lookbackDays    성과 분석 구간
 * @param minImpressions  이 노출수 미만이면 표본이 부족하다고 보고 추천하지 않는다
 * @param maxChangeRate   1회 조정 상한 비율 (0.2 = ±20%)
 * @param minBid          입찰가 하한
 * @param maxBid          입찰가 상한
 * @param maxDailyApplies 하루에 반영할 수 있는 최대 건수
 * @param dryRun          true 면 승인해도 네이버에 실제 반영하지 않고 기록만 남긴다
 */
@ConfigurationProperties("ads.bid")
public record BidPolicyProperties(
        long targetCpa,
        int lookbackDays,
        long minImpressions,
        double maxChangeRate,
        long minBid,
        long maxBid,
        int maxDailyApplies,
        boolean dryRun
) {}
