package com.marketingagent.domain.ads.autopilot;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopilotRunRepository extends JpaRepository<AutopilotRun, Long> {

    List<AutopilotRun> findTop30ByOrderByIdDesc();

    List<AutopilotRun> findByPreviewFalseAndRunDateOrderByIdAsc(LocalDate runDate);
}
