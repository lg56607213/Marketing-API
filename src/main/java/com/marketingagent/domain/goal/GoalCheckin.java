package com.marketingagent.domain.goal;

import com.marketingagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 알려 준 실제 결과. 네이버는 문의(전환)까지만 알고, 그 문의가 계약이 됐는지는 사람만 안다.
 * 이 기록이 쌓이면 가정했던 문의→계약 비율을 실제 값으로 바꾸고 목표 CPA 를 다시 계산한다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "goal_checkins", indexes = @Index(name = "ix_goal_checkins_account", columnList = "ad_account_id, id"))
public class GoalCheckin extends BaseEntity {

    public static final String LEADS_NAVER = "NAVER";
    public static final String LEADS_USER = "USER";

    @Column(name = "ad_account_id", nullable = false)
    private Long adAccountId;

    @Column(name = "since_date", nullable = false)
    private LocalDate since;

    @Column(name = "until_date", nullable = false)
    private LocalDate until;

    /** 광고로 들어온 문의 수 */
    @Column(nullable = false)
    private long leads;

    /** NAVER(네이버 전환 수를 그대로 씀) 또는 USER(사용자가 직접 셈) */
    @Column(name = "leads_source", nullable = false, length = 10)
    private String leadsSource;

    /** 그중 계약된 수 */
    @Column(nullable = false)
    private long deals;

    /** 그 계약들의 매출. 모르면 null */
    private Long revenue;

    public GoalCheckin(Long adAccountId, LocalDate since, LocalDate until, long leads, String leadsSource, long deals,
            Long revenue) {
        this.adAccountId = adAccountId;
        this.since = since;
        this.until = until;
        this.leads = leads;
        this.leadsSource = leadsSource;
        this.deals = deals;
        this.revenue = revenue;
    }
}
