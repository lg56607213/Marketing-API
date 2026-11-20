package com.marketingagent.domain.ads.planner;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 받아 둔 운영 계획. 가장 최근 것이 현재 계획이고, 브리핑이 이 목표와 실적을 비교한다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "marketing_plans")
public class MarketingPlan extends BaseEntity {

    /** 어느 광고 계정의 데이터인지. 업체마다, 계정마다 따로 쌓인다. */
    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;


    @Column(name = "goal_type", nullable = false, length = 20)
    private String goalType;

    @Column(name = "monthly_budget", nullable = false)
    private long monthlyBudget;

    @Column(name = "target_conversions", nullable = false)
    private long targetConversions;

    @Column(name = "target_cpa", nullable = false)
    private long targetCpa;

    /** 제안 전체(PlanProposal)를 JSON 으로 보관한다. */
    @Lob
    @Column(name = "proposal_json", nullable = false, columnDefinition = "TEXT")
    private String proposalJson;

    public MarketingPlan(Long adAccountId, String goalType, long monthlyBudget, long targetConversions, long targetCpa,
            String proposalJson) {
        this.adAccountId = adAccountId;
        this.goalType = goalType;
        this.monthlyBudget = monthlyBudget;
        this.targetConversions = targetConversions;
        this.targetCpa = targetCpa;
        this.proposalJson = proposalJson;
    }
}
