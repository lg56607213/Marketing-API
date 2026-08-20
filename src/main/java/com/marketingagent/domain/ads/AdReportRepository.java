package com.marketingagent.domain.ads;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdReportRepository extends JpaRepository<AdReport, Long> {
    List<AdReport> findTop20ByOrderByIdDesc();
}
