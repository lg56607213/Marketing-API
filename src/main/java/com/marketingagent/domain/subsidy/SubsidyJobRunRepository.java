package com.marketingagent.domain.subsidy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubsidyJobRunRepository extends JpaRepository<SubsidyJobRun, Long> {
    List<SubsidyJobRun> findTop30ByOrderByIdDesc();
}
