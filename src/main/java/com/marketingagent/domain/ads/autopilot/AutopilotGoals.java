package com.marketingagent.domain.ads.autopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.ads.AdCampaign;
import com.marketingagent.domain.ads.AdCampaignRepository;
import com.marketingagent.domain.ads.AdGroup;
import com.marketingagent.domain.ads.AdGroupRepository;
import com.marketingagent.domain.goal.GoalSession;
import com.marketingagent.domain.goal.GoalSessionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 목표 상담에서 나온 값을 자동 운영이 바로 쓸 수 있게 꺼낸다.
 *
 * <p>초보 업체는 상담만 하고 제안안을 확정하지 않는 경우가 많다. 그래도 상담에서 계산한 목표 CPA·손익분기·월 상한과
 * 업종·키워드 정보는 있으므로, 확정 여부와 관계없이 가장 최근 상담 결과를 쓴다(확정한 상담이 있으면 그것을 먼저).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutopilotGoals {

    /** 전환율 실측이 없을 때 쓰는 가정값. 목표 상담 분석과 같은 값이다. */
    static final double ASSUMED_CVR = 0.02;

    private final GoalSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final com.marketingagent.domain.ads.KeywordStatDailyRepository statRepository;
    private final com.marketingagent.domain.ads.BidPolicyProperties policy;
    private final AdCampaignRepository campaignRepository;
    private final AdGroupRepository adGroupRepository;

    /**
     * 클릭 1회에 낼 수 있는 최대 금액.
     *
     * <p>업체가 정했으면 그 값을 쓴다. 아니면 손익분기 CPA × 전환율이다. 이보다 비싸게 클릭을 사면 문의가 와도
     * 첫 계약만으로는 손해이기 때문이다. 전환율은 최근 30일 실측(전환 3건·클릭 50회 이상)이 있으면 그것을,
     * 없으면 가정값 2%를 쓴다. 근거가 하나도 없으면 0 을 돌려주고, 쓰는 쪽은 입찰가를 올리지 않는다.
     */
    public long maxCpc(Long accountId, AutopilotSettings settings) {
        if (settings != null && settings.getMaxCpc() > 0) {
            return settings.getMaxCpc();
        }
        Goals g = of(accountId);
        long breakEven = g.breakEvenCpa() > 0 ? g.breakEvenCpa() : g.targetCpa() * 2;
        return fromBreakEven(accountId, breakEven);
    }

    /**
     * 광고그룹별 클릭 1회 최대 금액. 한 계정에 사이트(캠페인)가 여럿이면 상품마다 남는 돈이 달라서
     * 캠페인에 정해 둔 값(최대 금액 → 손익분기 순)을 먼저 쓰고, 없으면 계정 기본값({@link #maxCpc})을 쓴다.
     */
    public Ceiling ceiling(Long accountId, AutopilotSettings settings) {
        long base = maxCpc(accountId, settings);
        Map<String, Long> byCampaign = new HashMap<>();
        for (AdCampaign c : campaignRepository.findByAdAccountIdOrderByNameAsc(accountId)) {
            long value = c.getMaxCpc() != null && c.getMaxCpc() > 0 ? c.getMaxCpc()
                    : c.getBreakEvenCpa() != null && c.getBreakEvenCpa() > 0 ? fromBreakEven(accountId, c.getBreakEvenCpa())
                    : 0;
            if (value > 0) {
                byCampaign.put(c.getNccCampaignId(), value);
            }
        }
        Map<String, Long> byAdgroup = new HashMap<>();
        if (!byCampaign.isEmpty()) {
            for (AdGroup g : adGroupRepository.findByAdAccountIdOrderByNameAsc(accountId)) {
                Long value = byCampaign.get(g.getNccCampaignId());
                if (value != null) {
                    byAdgroup.put(g.getNccAdgroupId(), value);
                }
            }
        }
        return new Ceiling(base, Map.copyOf(byAdgroup));
    }

    /** @param base 계정 기본값. 0 이면 근거 없음 */
    public record Ceiling(long base, Map<String, Long> byAdgroup) {
        public long of(String nccAdgroupId) {
            return nccAdgroupId == null ? base : byAdgroup.getOrDefault(nccAdgroupId, base);
        }

        /** 어느 그룹이든 기준이 하나라도 있는지. */
        public boolean any() {
            return base > 0 || byAdgroup.values().stream().anyMatch(v -> v > 0);
        }
    }

    /**
     * 캠페인마다 적어 둔 상품 설명. 키워드 판단 AI 가 검색어가 들어온 그룹의 상품 기준으로 보게 한다.
     *
     * @return 광고그룹 ID → "캠페인 X / 광고그룹 Y" 표기와, 설명 줄 목록
     */
    public CampaignContext campaignContext(Long accountId) {
        Map<String, AdCampaign> campaigns = new HashMap<>();
        campaignRepository.findByAdAccountIdOrderByNameAsc(accountId).forEach(c -> campaigns.put(c.getNccCampaignId(), c));
        Map<String, String> labels = new HashMap<>();
        for (AdGroup g : adGroupRepository.findByAdAccountIdOrderByNameAsc(accountId)) {
            AdCampaign c = campaigns.get(g.getNccCampaignId());
            labels.put(g.getNccAdgroupId(), (c != null ? "캠페인 " + c.getName() + " / " : "") + "광고그룹 " + g.getName());
        }
        List<String> notes = new ArrayList<>();
        campaigns.values().stream()
                .filter(c -> c.getBusinessNote() != null && !c.getBusinessNote().isBlank())
                .forEach(c -> notes.add("캠페인 " + c.getName() + ": " + c.getBusinessNote()));
        return new CampaignContext(Map.copyOf(labels), List.copyOf(notes));
    }

    public record CampaignContext(Map<String, String> adgroupLabels, List<String> notes) {
        static final CampaignContext EMPTY = new CampaignContext(Map.of(), List.of());
    }

    private long fromBreakEven(Long accountId, long breakEven) {
        if (breakEven <= 0) {
            return 0;
        }
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        long clicks = 0;
        long conversions = 0;
        for (var row : statRepository.aggregateByKeyword(accountId, yesterday.minusDays(29), yesterday)) {
            clicks += row.clkCnt() != null ? row.clkCnt() : 0;
            conversions += row.ccnt() != null ? row.ccnt() : 0;
        }
        double cvr = conversions >= 3 && clicks >= 50 ? conversions / (double) clicks : ASSUMED_CVR;
        long cpc = Math.round(breakEven * cvr / 10.0) * 10;
        return Math.max(policy.minBid(), Math.min(policy.maxBid(), cpc));
    }

    /**
     * @param targetCpa    목표 전환당 비용. 없으면 0
     * @param breakEvenCpa 손익분기 전환당 비용. 이보다 비싸게 사면 첫 계약만으로는 손해. 없으면 0
     * @param budgetLimit  업체가 말한 한 달 광고비 상한. 없으면 0
     * @param industry     업종 설명
     * @param context      키워드 판단에 쓸 사업 설명(상품·고객층·고민)
     * @param seedKeywords 상담에서 나온 키워드 후보
     */
    public record Goals(long targetCpa, long breakEvenCpa, long budgetLimit, String industry, String context,
                        List<String> seedKeywords) {
        static final Goals EMPTY = new Goals(0, 0, 0, null, null, List.of());
    }

    public Goals of(Long accountId) {
        Optional<GoalSession> session = sessionRepository
                .findTopByAdAccountIdAndStatusOrderByIdDesc(accountId, GoalSession.CONFIRMED)
                .or(() -> sessionRepository.findTopByAdAccountIdOrderByIdDesc(accountId));
        if (session.isEmpty()) {
            return Goals.EMPTY;
        }
        try {
            JsonNode model = read(session.get().getModelJson());
            JsonNode economics = read(session.get().getAnalysisJson()).path("economics");
            String industry = text(model.path("industry"));

            List<String> context = new ArrayList<>();
            if (industry != null) {
                context.add("업종: " + industry);
            }
            List<String> offerings = new ArrayList<>();
            model.path("offerings").forEach(o -> offerings.add(text(o.path("name"))));
            if (!offerings.isEmpty()) {
                context.add("상품: " + String.join(", ", offerings));
            }
            List<String> segments = new ArrayList<>();
            model.path("segments").forEach(s -> segments.add(s.asText()));
            if (!segments.isEmpty()) {
                context.add("고객층: " + String.join(", ", segments));
            }
            String concerns = text(model.path("concerns"));
            if (concerns != null) {
                context.add("업체가 말한 고민: " + concerns);
            }

            Set<String> seeds = new LinkedHashSet<>();
            model.path("seedKeywords").forEach(k -> seeds.add(k.asText().trim()));
            model.path("keywordGroups").forEach(g -> g.path("keywords").forEach(k -> seeds.add(k.asText().trim())));
            seeds.remove("");

            return new Goals(economics.path("targetCpa").asLong(0), economics.path("breakEvenCpa").asLong(0),
                    model.path("budgetLimit").asLong(0), industry, String.join("\n", context), List.copyOf(seeds));
        } catch (RuntimeException e) {
            log.warn("목표 상담 결과를 읽지 못했습니다: 계정 {} {}", accountId, e.getMessage());
            return Goals.EMPTY;
        }
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText().trim();
    }
}
