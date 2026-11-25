package com.marketingagent.integration.naver;

import com.marketingagent.integration.naver.dto.ConversionRow;
import com.marketingagent.integration.naver.dto.HourlyStatRow;
import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import com.marketingagent.integration.naver.dto.ReportProbe;
import com.marketingagent.integration.naver.dto.SearchQueryRow;
import com.marketingagent.integration.naver.dto.StatRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 검색광고 조회 클라이언트. AiProvider 와 동일하게 실제 구현과 Stub 을 교체할 수 있다.
 */
public interface SearchAdClient {

    List<NccCampaign> listCampaigns();

    List<NccAdgroup> listAdgroups(String nccCampaignId);

    List<NccKeyword> listKeywords(String nccAdgroupId);

    /**
     * 대상 ID 목록의 일자별 성과를 조회한다.
     *
     * @param ids 키워드/광고그룹/캠페인의 ncc ID 목록
     */
    List<StatRow> dailyStats(List<String> ids, LocalDate since, LocalDate until);

    /**
     * 키워드 입찰가를 변경한다. 사용자 승인을 거친 건에서만 호출해야 한다.
     *
     * @return 반영 후 입찰가
     */
    long updateKeywordBid(String nccKeywordId, String nccAdgroupId, String keyword, long bidAmt);

    /**
     * 목표 평균노출순위를 맞추기 위한 추정 입찰가를 네이버에서 받아온다.
     *
     * @param device PC 또는 MOBILE
     * @return 키워드 문자열 -> 추정 입찰가
     */
    Map<String, Long> estimateBidForPosition(List<String> keywords, int position, String device);

    /**
     * 키워드를 주어진 입찰가로 걸었을 때의 예상 월간 노출·클릭·비용을 네이버에서 받아온다.
     * 응답에 없는 조합은 결과에서 빠진다.
     *
     * @param device PC, MOBILE 또는 BOTH
     */
    List<PerformanceEstimate> estimatePerformance(List<PerformanceEstimate.Query> queries, String device);

    /**
     * 캠페인 일예산을 바꾼다. 자동 운영 또는 사용자 승인을 거친 건에서만 호출해야 한다.
     *
     * @return 반영 후 일예산
     */
    long updateCampaignDailyBudget(String nccCampaignId, long dailyBudget);

    /** 키워드 노출을 끄거나 켠다. 자동 운영 또는 사용자 승인을 거친 건에서만 호출해야 한다. */
    void setKeywordPaused(String nccKeywordId, String nccAdgroupId, boolean paused);

    /**
     * 키워드도구로 연관 키워드와 월간 검색량을 조회한다.
     * 광고 집행과 무관하게 키워드 발굴에 쓸 수 있다.
     *
     * @param hints 최대 5개의 힌트 키워드
     */
    List<RelatedKeyword> relatedKeywords(List<String> hints);

    /**
     * 특정 일자의 검색어 리포트를 내려받는다.
     * 등록 키워드뿐 아니라 확장검색으로 매칭된 실제 검색어까지 포함한다.
     */
    List<SearchQueryRow> searchQueryReport(LocalDate date);

    /**
     * 특정 일자의 시간대별 성과 리포트(AD_DETAIL)를 내려받는다.
     * 소재·지역·매체 단위까지 쪼개진 줄이 그대로 오므로 호출하는 쪽에서 합산한다.
     */
    List<HourlyStatRow> hourlyStatReport(LocalDate date);

    /**
     * 특정 일자의 전환 상세 리포트(AD_CONVERSION_DETAIL)를 내려받는다.
     * 전환수와 전환매출액이 전환방식·전환유형별로 담긴다.
     */
    List<ConversionRow> conversionReport(LocalDate date);

    /** 리포트를 내려받아 줄 수와 컬럼 수만 확인한다. 연결 점검용. */
    ReportProbe probeReport(String reportTp, LocalDate date);

    /** 광고그룹에 키워드를 추가한다. */
    List<NccKeyword> createKeywords(String nccAdgroupId, List<NewKeyword> keywords);

    /** 키워드를 삭제한다. */
    void deleteKeyword(String nccKeywordId);

    /** 광고그룹의 제외 키워드를 조회한다. */
    List<RestrictedKeyword> listRestrictedKeywords(String nccAdgroupId);

    /** 제외 키워드를 추가한다. type 은 EXP_SEARCH 또는 KEYWORD_PLUS_RESTRICT. */
    List<RestrictedKeyword> addRestrictedKeywords(String nccAdgroupId, List<String> keywords, String type);

    /** 제외 키워드를 삭제한다. */
    void deleteRestrictedKeyword(String nccAdgroupId, String restrictedKeywordId);

    /**
     * 남은 광고비(비즈머니) 잔액. 조회할 수 없으면 -1.
     * 이 잔액이 바닥나면 광고가 멈추므로 매일 보고 메일에 남은 일수와 함께 알린다.
     */
    default long bizmoneyBalance() {
        return -1;
    }

    /** 등록 요청용 키워드. bidAmt 가 null 이면 그룹 입찰가를 따른다. */
    record NewKeyword(String keyword, Long bidAmt) {}

    record RestrictedKeyword(String id, String keyword, String type) {}
}
