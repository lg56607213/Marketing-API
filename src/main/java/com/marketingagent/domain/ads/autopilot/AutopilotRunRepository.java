package com.marketingagent.domain.ads.autopilot;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopilotRunRepository extends JpaRepository<AutopilotRun, Long> {

    List<AutopilotRun> findTop30ByAdAccountIdOrderByIdDesc(Long adAccountId);

    List<AutopilotRun> findByAdAccountIdAndPreviewFalseAndRunDateOrderByIdAsc(Long adAccountId, LocalDate runDate);

    void deleteByAdAccountId(Long adAccountId);
}
