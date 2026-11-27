package com.marketingagent.domain.ads.launch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 새 광고 하나를 시작하는 계획. src/main/resources/ads-launch/*.json 에 둔다.
 *
 * @param accountId    광고 계정 ID
 * @param campaignName 광고그룹을 넣을 네이버 캠페인 이름(이미 있어야 한다)
 * @param adgroupName  만들 광고그룹 이름. 같은 이름이 있으면 새로 만들지 않고 그 그룹을 채운다
 * @param siteUrl      연결할 비즈채널(사이트) 주소
 * @param landingUrl   광고를 누르면 열릴 주소
 * @param groupBid     광고그룹 기본 입찰가
 * @param breakEvenCpa 전환 1건으로 남는 돈. 캠페인 기준으로 저장해 자동 운영이 쓴다
 * @param maxCpc       클릭 1회 최대 금액. 캠페인 기준으로 저장한다
 * @param businessNote 키워드 판단에 쓸 이 캠페인의 상품·고객 설명
 * @param keywords     시작 키워드
 * @param ads          텍스트 소재(제목 15자·설명 45자 이내)
 * @param analysis     있으면 키워드 분석·제안의 추천안으로 키워드와 입찰가를 정한다
 * @param monthlyBudget 분석에 쓸 한 달 광고비. 없으면 캠페인 하루 예산 × 30
 * @param saveEconomics false 면 캠페인의 손익 기준을 저장하지 않는다(같은 캠페인에 다른 상품 그룹을 더할 때). 기본 true
 * @param prune        false 면 그룹에 있던 키워드 중 추천에 없는 것을 지우지 않는다(기존 그룹용). 기본 true
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LaunchPlan(
        Long accountId,
        String campaignName,
        String adgroupName,
        String siteUrl,
        String landingUrl,
        long groupBid,
        Long breakEvenCpa,
        Long maxCpc,
        String businessNote,
        List<String> keywords,
        List<TextAd> ads,
        Analysis analysis,
        Long monthlyBudget,
        Boolean prune,
        Boolean saveEconomics
) {
    /**
     * 키워드를 데이터로 고르는 기준. 있으면 keywords 는 후보로만 쓰고, 넣을 키워드와 입찰가는 분석 결과로 정한다.
     *
     * @param hints     키워드도구에 넣을 힌트(5개씩 나눠 조회)
     * @param bidLadder 성과 예측을 받을 입찰가 단계
     * @param assumedCvr 실측 전환율이 없을 때의 가정 전환율
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Analysis(List<String> hints, List<String> requireAny, List<String> focusAny,
                           List<String> excludeWords, List<String> audienceTerms, List<String> productTerms,
                           double audienceWeight, double productWeight, long minMonthlySearches,
                           List<Long> bidLadder, double assumedCvr, int maxKeywords, int maxCandidates) {}

    public record TextAd(String headline, String description) {}
}
