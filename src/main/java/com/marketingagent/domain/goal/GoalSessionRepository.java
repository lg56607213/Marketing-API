package com.marketingagent.domain.goal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalSessionRepository extends JpaRepository<GoalSession, Long> {

    Optional<GoalSession> findTopByAdAccountIdOrderByIdDesc(Long adAccountId);

    Optional<GoalSession> findTopByAdAccountIdAndStatusOrderByIdDesc(Long adAccountId, String status);

    void deleteByAdAccountId(Long adAccountId);
}
