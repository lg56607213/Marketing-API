package com.marketingagent.domain.ads.planner;

import com.marketingagent.domain.ads.EfficiencyBidService.KeywordEfficiency;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos;
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
     * @param assumedCvrPct     실적도 사이트 실측도 없을 때 쓸 전환율 가정(%). 비우면 2%
     * @param siteMonthlyVisitors    사이트의 최근 한 달 방문자수. 전환수와 함께 주면 가정 대신 이 실측 전환율을 쓴다
     * @param siteMonthlyConversions 같은 기간 사이트에서 들어온 문의·견적·구매 건수
     * @param targetRoasPct     목표 광고수익률(%). saleValue 와 함께 주면 이 수익률이 나오는 클릭 단가까지만 산다
     */
    public record PlanRequest(
            @NotEmpty @Size(max = 5) List<String> seedKeywords,
            @NotNull GoalType goalType,
            Long targetConversions,
            Long monthlyBudget,
            Long saleValue,
            Double assumedCvrPct,
            Long siteMonthlyVisitors,
            Long siteMonthlyConversions,
            Double targetRoasPct
    ) {}

    /** 계획에 넣을지 검토한 키워드 한 건. */
    public record PlanKeyword(
            String keyword,
            long monthlySearches,
            String competition,
            long suggestedBid,
            long expectedClicks,
            long expectedCost,
            boolean selected,
            long expectedImpressions,
            long expectedCpc
    ) {}

    /**
     * 계획에 쓴 숫자 하나의 출처.
     *
     * @param grade 실측(내 계정·내 사이트의 실제 값) / 네이버 예측 / 가정
     */
    public record Basis(String item, String value, String source, String grade) {}

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
     * @param cvrLowPct   전환율 범위의 아래쪽. 실측이면 90% 신뢰구간, 가정이면 절반
     * @param maxCpc      수익이 남는 클릭 단가 한도. 전환 1건의 매출을 모르면 null
     * @param basis       숫자별 출처. 무엇이 실측이고 무엇이 가정인지 보여 준다
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
            LocalDateTime createdAt,
            double cvrLowPct,
            double cvrHighPct,
            Long maxCpc,
            List<Basis> basis
    ) {}

    public enum BriefingPeriod {
        DAILY, WEEKLY,
        /** 최근 30일을 그 앞 30일과 비교하는 월간 분석 보고서 */
        MONTHLY
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
     * @param efficiencyBasis      키워드 효율을 무엇에 비춰 계산했는지. 계산하지 못했으면 그 이유
     * @param efficiency           키워드별 광고비 대비 효율. 광고비가 큰 순
     * @param autopilotSummary     자동 운영이 오늘 한 일 요약. 실행하지 않았으면 null
     * @param autopilotActions     자동 운영이 오늘 바꾼 것
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
            LocalDateTime createdAt,
            String efficiencyBasis,
            List<KeywordEfficiency> efficiency,
            String autopilotSummary,
            List<AutopilotDtos.Action> autopilotActions
    ) {}
}
