package com.marketingagent.domain.ads.autopilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.goal.GoalSession;
import com.marketingagent.domain.goal.GoalSessionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
