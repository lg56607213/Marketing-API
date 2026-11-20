package com.marketingagent.domain.ads.planner;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingPlanRepository extends JpaRepository<MarketingPlan, Long> {

    /** 가장 최근에 받은 계획이 현재 계획이다. */
    Optional<MarketingPlan> findTopByAdAccountIdOrderByIdDesc(Long adAccountId);

    void deleteByAdAccountId(Long adAccountId);
}
