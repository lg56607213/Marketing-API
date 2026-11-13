package com.marketingagent.domain.ads.planner;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PlannerDtos {

    private PlannerDtos() {
    }

    public enum GoalType {
        /** 월 목표 전환수(문의·견적·구매 건수)에서 필요한 예산을 거꾸로 계산한다. */
        CONVERSIONS,
        /** 정해진 월 예산으로 기대할 수 있는 성과를 계산한다. */
        BUDGET
    }

    /**
     * 운영 계획 요청.
     *
     * @param seedKeywords      업종·상품을 나타내는 핵심 키워드 1~5개
     * @param targetConversions goalType 이 CONVERSIONS 일 때 월 목표 전환수
     * @param monthlyBudget     goalType 이 BUDGET 일 때 월 예산(원)
     * @param saleValue         전환 1건의 평균 매출(원). 있으면 매출·ROAS 까지 계산한다
     * @param assumedCvrPct     실적이 없을 때 쓸 전환율 가정(%). 비우면 2%
     */
    public record PlanRequest(
            @NotEmpty @Size(max = 5) List<String> seedKeywords,
            @NotNull GoalType goalType,
            Long targetConversions,
            Long monthlyBudget,
            Long saleValue,
            Double assumedCvrPct
    ) {}

    /** 계획에 넣을지 검토한 키워드 한 건. */
    public record PlanKeyword(
            String keyword,
            long monthlySearches,
            String competition,
            long suggestedBid,
            long expectedClicks,
            long expectedCost,
            boolean selected
    ) {}

    /** 전환율이 달라졌을 때의 기대 성과. */
    public record Scenario(
            String name,
            double cvrPct,
            long conversions,
            long cpa,
            Long revenue,
            Double roasPct
    ) {}

    /**
     * 운영 계획 제안.
     *
     * @param cvrSource   전환율을 어디서 가져왔는지 (실적 또는 가정)
     * @param assumptions 계산에 쓴 가정. 사용자가 반드시 읽어야 한다
     * @param steps       운영 방법. 순서대로 실행한다
     * @param warnings    계획의 신뢰도를 떨어뜨리는 문제
     */
    public record PlanProposal(
            Long id,
            GoalType goalType,
            List<String> seedKeywords,
            long monthlyBudget,
            long dailyBudget,
            long expectedClicks,
            long expectedConversions,
            long targetCpa,
            double cvrPct,
            String cvrSource,
            List<Scenario> scenarios,
            List<PlanKeyword> keywords,
            List<String> assumptions,
            List<String> steps,
            List<String> warnings,
            LocalDateTime createdAt
    ) {}

    public enum BriefingPeriod {
        DAILY, WEEKLY
    }

    public enum Verdict {
        /** 광고비를 더 써도 된다 */
        INCREASE,
        /** 지금 수준 유지 */
        HOLD,
        /** 광고비를 줄이거나 새는 곳부터 막아야 한다 */
        DECREASE,
        /** 판단하기에 데이터가 부족하다 */
        NEED_DATA
    }

    /**
     * 지표 한 줄.
     *
     * @param previous  비교 기준값. 일간은 직전 7일의 하루 평균, 주간은 직전 7일 합계
     * @param changePct 증감률(%). 비교 기준이 0이면 null
     */
    public record Metric(String label, double current, double previous, String unit, Double changePct) {}

    /** 이번 달 목표 대비 진행 상황. 계획이 없으면 브리핑에 넣지 않는다. */
    public record Pacing(
            long monthlyBudget,
            long spentSoFar,
            long projectedSpend,
            long targetConversions,
            long conversionsSoFar,
            long projectedConversions,
            String comment
    ) {}

    /** 권하는 조치 한 건. */
    public record Action(String type, String target, String detail) {}

    /**
     * 일간·주간 브리핑.
     *
     * @param verdict              광고비 증감 판단
     * @param currentDailyBudget   지금 캠페인 일예산 합계. 설정이 없으면 0
     * @param suggestedDailyBudget 권하는 일예산. 바꿀 필요가 없으면 null
     * @param narrative            보고서 본문. 문단 단위
     * @param generatedBy          rule-based 또는 AI 모델 이름
     */
    public record Briefing(
            Long id,
            BriefingPeriod period,
            LocalDate since,
            LocalDate until,
            String headline,
            Verdict verdict,
            String verdictReason,
            long currentDailyBudget,
            Long suggestedDailyBudget,
            List<Metric> metrics,
            Pacing pacing,
            String ctrComment,
            String cvrComment,
            List<Action> actions,
            List<String> narrative,
            String generatedBy,
            LocalDateTime createdAt
    ) {}
}
