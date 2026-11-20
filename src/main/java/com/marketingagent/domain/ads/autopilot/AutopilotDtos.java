package com.marketingagent.domain.ads.autopilot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;

public final class AutopilotDtos {

    private AutopilotDtos() {
    }

    /**
     * 자동 운영 설정 변경 요청.
     *
     * @param targetCpa        목표 전환당 비용(원). 0 이면 운영 계획의 목표를 따른다
     * @param targetRoasPct    목표 광고수익률(%). 0 이면 쓰지 않는다
     * @param monthlyBudgetCap 한 달 광고비 한도(원). 0 이면 운영 계획의 월 예산을 따른다
     */
    public record SettingsRequest(
            boolean enabled,
            @Min(0) long targetCpa,
            @Min(0) @Max(10_000) double targetRoasPct,
            @Min(0) long monthlyBudgetCap,
            boolean manageBids,
            boolean manageBudget,
            boolean pauseWasteful
    ) {}

    /**
     * 자동 운영 설정과 지금 실제로 적용되는 값.
     *
     * @param effectiveTargetCpa        지금 기준으로 쓰는 목표 전환당 비용. 정해진 것이 없으면 0
     * @param effectiveMonthlyBudgetCap 지금 적용되는 월 한도. 정해진 것이 없으면 0
     * @param targetSource              목표를 어디서 가져왔는지
     * @param dryRun                    서버가 dry-run 이라 실제로는 반영되지 않는 상태인지
     * @param maxChangeRatePct          입찰가를 한 번에 바꾸는 최대 폭(%)
     * @param maxDailyApplies           하루에 바꿀 수 있는 입찰가 건수
     */
    public record Settings(
            boolean enabled,
            long targetCpa,
            double targetRoasPct,
            long monthlyBudgetCap,
            boolean manageBids,
            boolean manageBudget,
            boolean pauseWasteful,
            long effectiveTargetCpa,
            long effectiveMonthlyBudgetCap,
            String targetSource,
            boolean dryRun,
            double maxChangeRatePct,
            int maxDailyApplies,
            long minBid,
            long maxBid
    ) {}

    /**
     * 자동 운영이 한 조치 한 건.
     *
     * @param type   입찰가 / 키워드 중지 / 일예산
     * @param status 반영됨 / 기록만(dry-run) / 미리보기 / 실패
     */
    public record Action(String type, String target, String before, String after, String reason, String status) {}

    /**
     * 자동 운영 한 번의 결과.
     *
     * @param notes 하지 않은 일과 그 이유. 사람이 확인해야 하는 것도 여기에 적는다
     */
    public record RunResult(
            Long id,
            boolean preview,
            boolean dryRun,
            String summary,
            List<Action> actions,
            List<String> notes,
            LocalDateTime ranAt
    ) {}
}
