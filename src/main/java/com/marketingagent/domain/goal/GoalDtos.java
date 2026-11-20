package com.marketingagent.domain.goal;

import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record MessageRequest(@NotBlank @Size(max = 2000) String text) {}

    /** @param enableAutopilot true 면 확정과 함께 자동 운영을 켠다. false 면 켜져 있던 설정을 그대로 둔다 */
    public record ConfirmRequest(@NotBlank String option, boolean enableAutopilot) {}

    /**
     * 실제 결과 알려 주기.
     *
     * @param leads 그 기간 광고로 들어온 문의 수. 비우면 네이버에 잡힌 전환 수를 쓴다
     */
    public record CheckinRequest(
            @NotNull LocalDate since,
            @NotNull LocalDate until,
            @Min(0) Long leads,
            @NotNull @Min(0) Long deals,
            @Min(0) Long revenue
    ) {}

    public record Message(Long id, String role, String content, LocalDateTime createdAt) {}

    /**
     * 화면이 받는 상담 전체.
     *
     * @param nextQuestion 아직 채울 항목. 다 채웠으면 null
     * @param economics    사업 구조가 다 채워졌을 때의 계산. 아니면 null
     * @param aiMode       ai(모델이 대화) 또는 rule(정해진 질문)
     */
    public record GoalView(
            Long sessionId,
            String status,
            BusinessModel model,
            String nextQuestion,
            GoalEconomics.Result economics,
            Analysis analysis,
            String confirmedOption,
            LocalDateTime confirmedAt,
            List<Message> messages,
            List<Checkin> checkins,
            String aiMode
    ) {}

    /**
     * 사업 구조로 계산한 목표와, 그 목표를 네이버 키워드 수요·예상 성과에 대 본 결과.
     *
     * @param recommended 추천 안의 key
     */
    public record Analysis(
            GoalEconomics.Result economics,
            List<Option> options,
            String recommended,
            String recommendReason,
            List<String> notes,
            LocalDateTime analyzedAt
    ) {}

    /**
     * 운영안 한 건. 숫자는 모두 서버가 계산한다.
     *
     * @param key             GOAL(목표 달성) / LIMIT(예산 상한) / TRIAL(시험 운영)
     * @param expectedLeads   한 달 기대 문의 수 (네이버 예측 클릭 × 전환율)
     * @param leadsLow        전환율이 범위 아래쪽일 때
     * @param leadsHigh       위쪽일 때
     * @param expectedProfit  기대 계약의 이익 합계에서 광고비를 뺀 값. 첫 계약만 센다
     * @param expectedCpa     문의 1건에 드는 광고비 예상
     * @param goalRatePct     목표 대비 기대 문의 비율
     * @param plan            키워드·입찰가까지 담긴 계획. 이 안을 만들 수 없으면 null 이고 error 에 사유가 있다
     */
    public record Option(
            String key,
            String label,
            String description,
            long monthlyBudget,
            long dailyBudget,
            long expectedCost,
            long expectedLeads,
            long leadsLow,
            long leadsHigh,
            long expectedDeals,
            long expectedRevenue,
            long expectedProfit,
            long expectedCpa,
            boolean meetsGoal,
            double goalRatePct,
            List<String> warnings,
            PlanProposal plan,
            String error
    ) {}

    public record Checkin(Long id, LocalDate since, LocalDate until, long leads, String leadsSource, long deals,
            Long revenue, double dealRatePct) {}

    /**
     * 실제 결과를 반영한 뒤의 변화.
     *
     * @param previousLeadToDealPct 반영 전 문의→계약 비율
     * @param leadToDealPct         반영 후(실측과 이전 값을 섞은 값)
     * @param appliedTargetCpa      지금 자동 운영·입찰 추천이 쓰는 목표 CPA
     * @param suggestedTargetCpa    새 비율로 다시 계산한 목표 CPA
     */
    public record CheckinResult(
            Checkin checkin,
            long totalLeads,
            long totalDeals,
            double measuredPct,
            double previousLeadToDealPct,
            double leadToDealPct,
            long appliedTargetCpa,
            long suggestedTargetCpa,
            String message,
            GoalView view
    ) {}
}
