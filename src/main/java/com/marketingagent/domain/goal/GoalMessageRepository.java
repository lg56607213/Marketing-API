package com.marketingagent.domain.goal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalMessageRepository extends JpaRepository<GoalMessage, Long> {

    List<GoalMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    void deleteByAdAccountId(Long adAccountId);
}
