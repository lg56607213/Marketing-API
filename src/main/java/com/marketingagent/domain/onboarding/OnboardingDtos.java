package com.marketingagent.domain.onboarding;

import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    /**
     * 업체 정보와 광고 목표.
     *
     * @param keywords               상품·서비스를 나타내는 핵심 키워드 1~5개. 사람들이 검색할 만한 말
     * @param monthlyBudget          한 달에 광고에 쓸 수 있는 돈(원)
     * @param targetConversions      한 달 목표 건수(문의·견적·구매)
     * @param saleValue              1건당 평균 매출(원). 선택
     * @param siteMonthlyVisitors    홈페이지의 최근 한 달 방문자수. 선택
     * @param siteMonthlyConversions 같은 기간 문의·구매 건수. 선택
     */
    public record ProfileRequest(
            @NotBlank @Size(max = 100) String companyName,
            @NotBlank @Size(max = 300) String homepageUrl,
            @NotBlank @Size(max = 2000) String businessDescription,
            @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 40) String> keywords,
            @Min(10_000) long monthlyBudget,
            @Min(1) long targetConversions,
            Long saleValue,
            Long siteMonthlyVisitors,
            Long siteMonthlyConversions
    ) {}

    public record Profile(
            String companyName,
            String homepageUrl,
            String businessDescription,
            List<String> keywords,
            long monthlyBudget,
            long targetConversions,
            Long saleValue,
            Long siteMonthlyVisitors,
            Long siteMonthlyConversions
    ) {}

    /**
     * 홈페이지를 한 번 열어 본 결과.
     *
     * @param reachable       열렸는지
     * @param naverTracking   네이버 전환·방문 추적 스크립트가 보이는지
     * @param googleAnalytics 구글 애널리틱스·태그매니저가 보이는지
     * @param hasForm         문의·신청 양식이 보이는지
     * @param notes           광고를 시작하기 전에 알아야 할 점
     */
    public record SiteCheck(
            boolean reachable,
            String title,
            String description,
            boolean naverTracking,
            boolean googleAnalytics,
            boolean hasForm,
            List<String> notes
    ) {}

    /**
     * 예산 대안 한 건.
     *
     * @param key                 INCREASE(증액) / REQUESTED(요청대로) / DECREASE(감액)
     * @param monthlyBudget       이 안의 월 예산 한도
     * @param expectedCost        그중 실제로 쓰일 것으로 예상되는 금액
     * @param conversionsLow      전환율이 범위의 아래쪽일 때의 전환수
     * @param goalRatePct         목표 건수 대비 기대 전환(%)
     * @param costPerConversion   전환 1건당 비용
     * @param revenue             기대 매출. 1건당 매출을 모르면 null
     * @param extraCostPerExtraConversion 바로 아래 안보다 돈을 더 써서 얻는 전환 1건의 값. 맨 아래 안이거나 전환이 늘지 않으면 null
     * @param effect              이 안의 효과를 한두 문장으로
     * @param plan                키워드·입찰가까지 담긴 운영 계획
     */
    public record Alternative(
            String key,
            String label,
            long monthlyBudget,
            long dailyBudget,
            long expectedCost,
            long expectedClicks,
            long expectedConversions,
            long conversionsLow,
            long conversionsHigh,
            double goalRatePct,
            long costPerConversion,
            Long revenue,
            Double roasPct,
            Long extraCostPerExtraConversion,
            int keywordCount,
            boolean recommended,
            String effect,
            PlanProposal plan
    ) {}

    /**
     * 업체 정보를 보고 만든 제안.
     *
     * @param recommendation 세 안 중 무엇을 왜 권하는지
     * @param warnings       제안의 신뢰도를 떨어뜨리는 문제
     * @param selectedOption 회원이 고른 안. 아직이면 null
     */
    public record Proposal(
            Profile profile,
            SiteCheck siteCheck,
            List<Alternative> alternatives,
            String recommendedKey,
            String recommendation,
            List<String> warnings,
            String selectedOption,
            LocalDateTime createdAt
    ) {}
}
