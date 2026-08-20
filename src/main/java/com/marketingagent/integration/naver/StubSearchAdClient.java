package com.marketingagent.integration.naver;

import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import com.marketingagent.integration.naver.dto.SearchQueryRow;
import com.marketingagent.integration.naver.dto.StatRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 자격증명 없이 화면과 파이프라인을 검증하기 위한 Stub.
 * 값은 ID 와 날짜로부터 결정적으로 생성되므로 새로고침해도 흔들리지 않는다.
 */
@Component
@ConditionalOnProperty(name = "naver.searchad.provider", havingValue = "stub", matchIfMissing = true)
public class StubSearchAdClient implements SearchAdClient {

    private static final String CAMPAIGN_ID = "cmp-a001-01-000000001";
    private static final String ADGROUP_ID = "grp-a001-01-000000001";

    /** Stub 에서 승인된 입찰가 변경을 기억해 화면에서 반영 결과를 확인할 수 있게 한다. */
    private final Map<String, Long> overriddenBids = new ConcurrentHashMap<>();

    /** Stub 에서 등록한 제외 키워드를 기억한다. */
    private final Map<String, List<RestrictedKeyword>> restricted = new ConcurrentHashMap<>();

    private static final List<String> KEYWORDS = List.of(
            "화물차", "1톤화물차", "중고화물차", "화물차매매", "탑차",
            "윙바디", "화물차렌트", "화물차할부", "냉동탑차", "카고트럭");

    @Override
    public List<NccCampaign> listCampaigns() {
        return List.of(new NccCampaign(CAMPAIGN_ID, "[Stub] 화물차 검색광고", "WEB_SITE", "ELIGIBLE", 50000L, false));
    }

    @Override
    public List<NccAdgroup> listAdgroups(String nccCampaignId) {
        if (!CAMPAIGN_ID.equals(nccCampaignId)) {
            return List.of();
        }
        return List.of(new NccAdgroup(ADGROUP_ID, CAMPAIGN_ID, "[Stub] 화물차 통합", "ELIGIBLE", 700L, 20000L, false));
    }

    @Override
    public List<NccKeyword> listKeywords(String nccAdgroupId) {
        if (!ADGROUP_ID.equals(nccAdgroupId)) {
            return List.of();
        }
        List<NccKeyword> keywords = new ArrayList<>();
        for (int i = 0; i < KEYWORDS.size(); i++) {
            keywords.add(new NccKeyword(
                    keywordId(i), ADGROUP_ID, KEYWORDS.get(i), "ELIGIBLE",
                    overriddenBids.getOrDefault(keywordId(i), 500L + (i * 90L)), false, false));
        }
        return keywords;
    }

    @Override
    public List<StatRow> dailyStats(List<String> ids, LocalDate since, LocalDate until) {
        List<StatRow> rows = new ArrayList<>();
        for (String id : ids) {
            for (LocalDate date = since; !date.isAfter(until); date = date.plusDays(1)) {
                rows.add(statRow(id, date));
            }
        }
        return rows;
    }

    @Override
    public Map<String, Long> estimateBidForPosition(List<String> keywords, int position, String device) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (String keyword : keywords) {
            long base = 500 + Math.abs(keyword.hashCode() % 900);
            result.put(keyword, Math.max(70, base * (6 - Math.min(position, 5))));
        }
        return result;
    }

    @Override
    public long updateKeywordBid(String nccKeywordId, String nccAdgroupId, String keyword, long bidAmt) {
        overriddenBids.put(nccKeywordId, bidAmt);
        return bidAmt;
    }

    @Override
    public List<RelatedKeyword> relatedKeywords(List<String> hints) {
        List<RelatedKeyword> result = new ArrayList<>();
        for (String hint : hints) {
            for (String suffix : List.of("", "가격", "리스", "중고", "추천")) {
                String keyword = hint + suffix;
                int seed = Math.abs(keyword.hashCode());
                result.add(new RelatedKeyword(keyword, 100 + seed % 4000, 300 + seed % 12000,
                        seed % 40, seed % 120, List.of("높음", "중간", "낮음").get(seed % 3)));
            }
        }
        return result;
    }

    @Override
    public List<SearchQueryRow> searchQueryReport(LocalDate date) {
        List<SearchQueryRow> rows = new ArrayList<>();
        for (int i = 0; i < KEYWORDS.size(); i++) {
            String query = KEYWORDS.get(i) + (i % 2 == 0 ? "" : " 추천");
            int seed = Math.abs((query + date).hashCode());
            long imp = 5 + seed % 200;
            long clk = seed % 5;
            rows.add(new SearchQueryRow(date, CAMPAIGN_ID, ADGROUP_ID, query,
                    i % 3 == 0 ? "P" : "M", imp, clk, clk * (500 + seed % 900)));
        }
        return rows;
    }

    @Override
    public List<NccKeyword> createKeywords(String nccAdgroupId, List<NewKeyword> keywords) {
        List<NccKeyword> created = new ArrayList<>();
        for (NewKeyword k : keywords) {
            created.add(new NccKeyword("nkw-stub-" + Math.abs(k.keyword().hashCode()), nccAdgroupId,
                    k.keyword(), "PAUSED", k.bidAmt() != null ? k.bidAmt() : 70L,
                    k.bidAmt() == null, false));
        }
        return created;
    }

    @Override
    public void deleteKeyword(String nccKeywordId) {
        overriddenBids.remove(nccKeywordId);
    }

    @Override
    public List<RestrictedKeyword> listRestrictedKeywords(String nccAdgroupId) {
        return List.copyOf(restricted.getOrDefault(nccAdgroupId, List.of()));
    }

    @Override
    public List<RestrictedKeyword> addRestrictedKeywords(String nccAdgroupId, List<String> keywords, String type) {
        List<RestrictedKeyword> added = new ArrayList<>();
        for (String keyword : keywords) {
            added.add(new RestrictedKeyword("rst-stub-" + Math.abs(keyword.hashCode()), keyword, type));
        }
        List<RestrictedKeyword> all = new ArrayList<>(restricted.getOrDefault(nccAdgroupId, List.of()));
        all.addAll(added);
        restricted.put(nccAdgroupId, all);
        return added;
    }

    @Override
    public void deleteRestrictedKeyword(String nccAdgroupId, String restrictedKeywordId) {
        List<RestrictedKeyword> all = new ArrayList<>(restricted.getOrDefault(nccAdgroupId, List.of()));
        all.removeIf(r -> r.id().equals(restrictedKeywordId));
        restricted.put(nccAdgroupId, all);
    }

    private StatRow statRow(String id, LocalDate date) {
        int seed = Math.abs((id + date).hashCode());
        long impCnt = 120 + (seed % 900);
        long clkCnt = Math.max(1, impCnt * (2 + (seed % 6)) / 100);
        long cpc = 400 + (seed % 700);
        long salesAmt = clkCnt * cpc;
        double ctr = round2(clkCnt * 100.0 / impCnt);
        double avgRnk = round2(1.0 + (seed % 60) / 10.0);
        long ccnt = clkCnt / 10;
        return new StatRow(id, date, impCnt, clkCnt, salesAmt, ctr, cpc, avgRnk, ccnt);
    }

    private String keywordId(int index) {
        return String.format("nkw-a001-01-%09d", index + 1);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
