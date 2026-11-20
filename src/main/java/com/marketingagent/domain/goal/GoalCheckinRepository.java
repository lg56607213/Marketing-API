package com.marketingagent.domain.goal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalCheckinRepository extends JpaRepository<GoalCheckin, Long> {

    List<GoalCheckin> findByAdAccountIdOrderBySinceDesc(Long adAccountId);

    void deleteByAdAccountId(Long adAccountId);
}
