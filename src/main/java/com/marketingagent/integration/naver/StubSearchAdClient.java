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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자격증명 없이 화면과 파이프라인을 검증하기 위한 Stub.
 * 값은 ID 와 날짜로부터 결정적으로 생성되므로 새로고침해도 흔들리지 않는다.
 */
public class StubSearchAdClient implements SearchAdClient {

    private final String campaignId;
    private final String adgroupId;
    private final String campaignId2;
    private final String adgroupId2;
    private final String keywordPrefix;

    /** 계정마다 ID 가 겹치지 않도록 계정 번호를 ID 에 넣는다. */
    public StubSearchAdClient(long accountId) {
        String tag = String.format("s%03d", accountId % 1000);
        this.campaignId = "cmp-" + tag + "-01-000000001";
        this.adgroupId = "grp-" + tag + "-01-000000001";
        this.campaignId2 = "cmp-" + tag + "-01-000000002";
        this.adgroupId2 = "grp-" + tag + "-01-000000002";
        this.keywordPrefix = "nkw-" + tag + "-01-";
    }

    /** Stub 에서 승인된 입찰가 변경을 기억해 화면에서 반영 결과를 확인할 수 있게 한다. */
    private final Map<String, Long> overriddenBids = new ConcurrentHashMap<>();

    /** Stub 에서 바꾼 캠페인 일예산과 꺼 둔 키워드를 기억한다. */
    private final Map<String, Long> overriddenBudgets = new ConcurrentHashMap<>();
    private final java.util.Set<String> pausedKeywords = ConcurrentHashMap.newKeySet();

    /** Stub 에서 등록한 제외 키워드를 기억한다. */
    private final Map<String, List<RestrictedKeyword>> restricted = new ConcurrentHashMap<>();

    private static final List<String> KEYWORDS = List.of(
            "화물차", "1톤화물차", "중고화물차", "화물차매매", "탑차",
            "윙바디", "화물차렌트", "화물차할부", "냉동탑차", "카고트럭");

    @Override
    public List<NccCampaign> listCampaigns() {
        return List.of(
                new NccCampaign(campaignId, "[Stub] 화물차 검색광고", "WEB_SITE", "ELIGIBLE",
                        overriddenBudgets.getOrDefault(scoped(campaignId), 50000L), false),
                new NccCampaign(campaignId2, "[Stub] 차량 관리 검색광고", "WEB_SITE", "ELIGIBLE",
                        overriddenBudgets.getOrDefault(scoped(campaignId2), 30000L), false));
    }

    @Override
    public List<NccAdgroup> listAdgroups(String nccCampaignId) {
        if (campaignId2.equals(nccCampaignId)) {
            return List.of(new NccAdgroup(adgroupId2, campaignId2, "[Stub] 차량 관리 통합", "ELIGIBLE", 600L, 15000L, false));
        }
        if (!campaignId.equals(nccCampaignId)) {
            return List.of();
        }
        return List.of(new NccAdgroup(adgroupId, campaignId, "[Stub] 화물차 통합", "ELIGIBLE", 700L, 20000L, false));
    }

    @Override
    public List<NccKeyword> listKeywords(String nccAdgroupId) {
        boolean second = adgroupId2.equals(nccAdgroupId);
        if (!second && !adgroupId.equals(nccAdgroupId)) {
            return List.of();
        }
        // first half of the keywords belongs to campaign 1, the rest to campaign 2
        int half = KEYWORDS.size() / 2;
        List<NccKeyword> keywords = new ArrayList<>();
        for (int i = second ? half : 0; i < (second ? KEYWORDS.size() : half); i++) {
            keywords.add(new NccKeyword(
                    keywordId(i), nccAdgroupId, KEYWORDS.get(i),
                    pausedKeywords.contains(scoped(keywordId(i))) ? "PAUSED" : "ELIGIBLE",
                    overriddenBids.getOrDefault(scoped(keywordId(i)), 500L + (i * 90L)), false, false));
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

    /**
     * 입찰가를 올릴수록 클릭이 늘되 점점 덜 늘고, 클릭 단가는 입찰가의 75% 로 잡은 가짜 예측.
     * 실제 경매처럼 입찰가를 올릴수록 추가 클릭의 단가가 비싸진다.
     */
    @Override
    public List<PerformanceEstimate> estimatePerformance(List<PerformanceEstimate.Query> queries, String device) {
        List<PerformanceEstimate> result = new ArrayList<>();
        for (PerformanceEstimate.Query q : queries) {
            int seed = Math.abs(q.keyword().hashCode());
            double maxClicks = 60 + seed % 400;
            double half = 300 + seed % 900;
            long clicks = Math.round(maxClicks * q.bid() / (q.bid() + half));
            long cpc = Math.max(70, Math.round(q.bid() * 0.75));
            result.add(new PerformanceEstimate(q.keyword(), q.bid(), clicks * 30, clicks, clicks * cpc));
        }
        return result;
    }

    @Override
    public long updateCampaignDailyBudget(String nccCampaignId, long dailyBudget) {
        overriddenBudgets.put(scoped(nccCampaignId), dailyBudget);
        return dailyBudget;
    }

    @Override
    public void setKeywordPaused(String nccKeywordId, String nccAdgroupId, boolean paused) {
        if (paused) {
            pausedKeywords.add(scoped(nccKeywordId));
        } else {
            pausedKeywords.remove(scoped(nccKeywordId));
        }
    }

    @Override
    public long updateKeywordBid(String nccKeywordId, String nccAdgroupId, String keyword, long bidAmt) {
        overriddenBids.put(scoped(nccKeywordId), bidAmt);
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
            boolean second = i >= KEYWORDS.size() / 2;
            rows.add(new SearchQueryRow(date, second ? campaignId2 : campaignId, second ? adgroupId2 : adgroupId, query,
                    i % 3 == 0 ? "P" : "M", imp, clk, clk * (500 + seed % 900)));
        }
        return rows;
    }

    /** 오전 9시~오후 10시에 노출이 몰리는 가짜 시간대별 성과. */
    @Override
    public List<HourlyStatRow> hourlyStatReport(LocalDate date) {
        List<HourlyStatRow> rows = new ArrayList<>();
        for (int i = 0; i < KEYWORDS.size(); i++) {
            for (int hour = 0; hour < 24; hour++) {
                for (String device : List.of("P", "M")) {
                    int seed = Math.abs((keywordId(i) + date + hour + device).hashCode());
                    boolean busy = hour >= 9 && hour <= 22;
                    long imp = (busy ? 20 : 2) + seed % (busy ? 40 : 6);
                    long clk = imp * (1 + seed % 6) / 100 + (seed % 7 == 0 ? 1 : 0);
                    long cost = clk * (400 + seed % 700);
                    long rnkSum = imp * (10 + seed % 40) / 10;
                    rows.add(new HourlyStatRow(date, hour, campaignOf(i), adgroupOf(i), keywordId(i), device,
                            imp, clk, cost, rnkSum));
                }
            }
        }
        return rows;
    }

    /** 짝수 번째 키워드만 전환이 나게 해서 비용만 쓰는 키워드가 드러나게 한다. */
    @Override
    public List<ConversionRow> conversionReport(LocalDate date) {
        List<ConversionRow> rows = new ArrayList<>();
        for (int i = 0; i < KEYWORDS.size(); i += 2) {
            for (int hour = 9; hour <= 22; hour++) {
                int seed = Math.abs((keywordId(i) + date + hour).hashCode());
                if (seed % 4 != 0) {
                    continue;
                }
                String device = seed % 3 == 0 ? "P" : "M";
                rows.add(new ConversionRow(date, hour, campaignOf(i), adgroupOf(i), keywordId(i), device,
                        1, "lead", 1, 0));
                if (seed % 8 == 0) {
                    rows.add(new ConversionRow(date, hour, campaignOf(i), adgroupOf(i), keywordId(i), device,
                            2, "purchase", 1, 30000L + seed % 70000));
                }
            }
        }
        return rows;
    }

    @Override
    public ReportProbe probeReport(String reportTp, LocalDate date) {
        return new ReportProbe(1, StatReportParser.expectedColumns(reportTp));
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

    /** 예시 계정의 비즈머니 잔액. 보고 메일 미리보기가 그럴듯하게 보이도록 고정값을 준다. */
    @Override
    public long bizmoneyBalance() {
        return 300_000;
    }

    @Override
    public void deleteKeyword(String nccKeywordId) {
        overriddenBids.remove(scoped(nccKeywordId));
    }

    @Override
    public List<RestrictedKeyword> listRestrictedKeywords(String nccAdgroupId) {
        return List.copyOf(restricted.getOrDefault(scoped(nccAdgroupId), List.of()));
    }

    @Override
    public List<RestrictedKeyword> addRestrictedKeywords(String nccAdgroupId, List<String> keywords, String type) {
        List<RestrictedKeyword> added = new ArrayList<>();
        for (String keyword : keywords) {
            added.add(new RestrictedKeyword("rst-stub-" + Math.abs(keyword.hashCode()), keyword, type));
        }
        List<RestrictedKeyword> all = new ArrayList<>(restricted.getOrDefault(scoped(nccAdgroupId), List.of()));
        all.addAll(added);
        restricted.put(scoped(nccAdgroupId), all);
        return added;
    }

    @Override
    public void deleteRestrictedKeyword(String nccAdgroupId, String restrictedKeywordId) {
        List<RestrictedKeyword> all = new ArrayList<>(restricted.getOrDefault(scoped(nccAdgroupId), List.of()));
        all.removeIf(r -> r.id().equals(restrictedKeywordId));
        restricted.put(scoped(nccAdgroupId), all);
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

    /** 인스턴스가 계정마다 따로 있어서 기억해 둔 값이 계정끼리 섞이지 않는다. */
    private String scoped(String id) {
        return id;
    }

    /** 키워드 앞쪽 절반은 첫 캠페인, 나머지는 둘째 캠페인에 속한다. */
    private boolean inSecondCampaign(int index) {
        return index >= KEYWORDS.size() / 2;
    }

    private String campaignOf(int index) {
        return inSecondCampaign(index) ? campaignId2 : campaignId;
    }

    private String adgroupOf(int index) {
        return inSecondCampaign(index) ? adgroupId2 : adgroupId;
    }

    private String keywordId(int index) {
        return keywordPrefix + String.format("%09d", index + 1);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
