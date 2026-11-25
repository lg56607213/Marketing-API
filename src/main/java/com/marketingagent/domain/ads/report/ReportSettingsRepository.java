package com.marketingagent.domain.ads.report;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportSettingsRepository extends JpaRepository<ReportSettings, Long> {
    Optional<ReportSettings> findByAdAccountId(Long adAccountId);
}
