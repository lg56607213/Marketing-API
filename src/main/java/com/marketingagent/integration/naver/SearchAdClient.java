package com.marketingagent.integration.naver;

import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
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
}
