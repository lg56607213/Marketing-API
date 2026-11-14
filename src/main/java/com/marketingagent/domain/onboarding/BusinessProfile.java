package com.marketingagent.domain.onboarding;

import com.marketingagent.common.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 가입하면서 적은 업체 정보와 광고 목표. 회원마다 하나다.
 * 마지막으로 받은 제안(예산 3안)도 함께 보관해 다시 들어왔을 때 그대로 보여 준다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "business_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_profiles_owner", columnNames = "owner_id"))
public class BusinessProfile extends OwnedEntity {

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "homepage_url", nullable = false, length = 300)
    private String homepageUrl;

    @Lob
    @Column(name = "business_description", nullable = false, columnDefinition = "TEXT")
    private String businessDescription;

    /** 핵심 키워드. 쉼표로 구분 */
    @Column(nullable = false, length = 300)
    private String keywords;

    @Column(name = "monthly_budget", nullable = false)
    private long monthlyBudget;

    @Column(name = "target_conversions", nullable = false)
    private long targetConversions;

    /** 전환 1건의 평균 매출. 모르면 null */
    @Column(name = "sale_value")
    private Long saleValue;

    @Column(name = "site_monthly_visitors")
    private Long siteMonthlyVisitors;

    @Column(name = "site_monthly_conversions")
    private Long siteMonthlyConversions;

    /** 마지막 제안 전체(OnboardingDtos.Proposal)를 JSON 으로 보관한다. */
    @Lob
    @Column(name = "proposal_json", columnDefinition = "TEXT")
    private String proposalJson;

    /** 회원이 고른 안(INCREASE / REQUESTED / DECREASE). 아직 고르지 않았으면 null */
    @Column(name = "selected_option", length = 20)
    private String selectedOption;

    public void update(String companyName, String homepageUrl, String businessDescription, String keywords,
            long monthlyBudget, long targetConversions, Long saleValue, Long siteMonthlyVisitors,
            Long siteMonthlyConversions) {
        this.companyName = companyName;
        this.homepageUrl = homepageUrl;
        this.businessDescription = businessDescription;
        this.keywords = keywords;
        this.monthlyBudget = monthlyBudget;
        this.targetConversions = targetConversions;
        this.saleValue = saleValue;
        this.siteMonthlyVisitors = siteMonthlyVisitors;
        this.siteMonthlyConversions = siteMonthlyConversions;
    }

    public void proposed(String proposalJson) {
        this.proposalJson = proposalJson;
        this.selectedOption = null;
    }

    public void selected(String option, String proposalJson) {
        this.selectedOption = option;
        this.proposalJson = proposalJson;
    }
}
