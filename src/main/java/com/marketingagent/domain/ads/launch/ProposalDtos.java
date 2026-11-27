package com.marketingagent.domain.ads.launch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 키워드 분석·제안 화면의 요청과 응답. 금액은 원, 클릭·비용은 한 달 예상. */
public final class ProposalDtos {

    private ProposalDtos() {}

    /**
     * @param nccCampaignId       예산·손익 기본값을 가져올 캠페인. 비우면 계정 전체 기준
     * @param nccAdgroupId        넣을 광고그룹. 이미 등록된 키워드 표시와 적용에 쓴다
     * @param hints               무엇을 파는지 보여 주는 힌트 키워드
     * @param focusTerms          키워드에 꼭 하나는 들어가야 하는 상품 말(예: 레이, 포터). 들어가면 전환 가중치
     * @param typeTerms           거래 방식 말(예: 렌트, 리스, 할부). 비우면 따지지 않는다
     * @param audienceTerms       대상 고객 말(예: 저신용, 회생). 들어가면 전환 가중치
     * @param excludeWords        들어가면 빼는 말
     * @param profitPerConversion 전환(계약) 1건으로 남는 돈. 0 이면 캠페인에 저장한 값
     * @param maxCpc              클릭 1회 최대 금액. 0 이면 남는 돈 × 전환율
     * @param monthlyBudget       한 달 광고비. 0 이면 캠페인 하루 예산 × 30
     * @param cvrPct              가정 전환율(%). 0 이면 2%
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProposalRequest(String nccCampaignId, String nccAdgroupId, List<String> hints,
                                  List<String> focusTerms, List<String> typeTerms, List<String> audienceTerms,
                                  List<String> excludeWords, long profitPerConversion, long maxCpc,
                                  long monthlyBudget, double cvrPct, int maxKeywords) {}

    /** 입찰가 한 단계에서의 예상. */
    public record Option(long bid, long clicks, long cost) {}

    /**
     * 키워드 한 줄.
     *
     * @param tag            PICK(추천) · OVER_CAP(최대 금액 안에서 클릭 없음) · LOW_VOLUME · LOW_EFFICIENCY
     * @param recommendedBid 추천 입찰가. 0 이면 추천하지 않음
     * @param options        고를 수 있는 입찰가 단계(최대 금액 이하)
     * @param minBidAboveCap 최대 금액 위에서 클릭이 생기기 시작하는 입찰가. 모르면 0
     */
    public record Item(String keyword, long monthlySearches, double ctrPct, String competition, double weight,
                       String tag, long recommendedBid, long clicks, long cost, String reason, boolean registered,
                       Long currentBid, List<Option> options, long minBidAboveCap, long clicksAboveCap,
                       long costAboveCap) {}

    public record Summary(int candidates, int picked, long clicks, long cost, long avgCpc, double conversions,
                          long profit, long monthlyBudget, long maxCpc, double cvrPct, long profitPerConversion) {}

    public record Proposal(String nccCampaignId, String nccAdgroupId, String adgroupName, Summary summary,
                           List<String> insights, List<Item> items) {}

    public record ApplyItem(String keyword, long bid) {}

    /** @param removeUnselected true 면 그룹에 있지만 고르지 않은 키워드를 지운다 */
    public record ApplyRequest(String nccAdgroupId, List<ApplyItem> items, boolean removeUnselected) {}
}
