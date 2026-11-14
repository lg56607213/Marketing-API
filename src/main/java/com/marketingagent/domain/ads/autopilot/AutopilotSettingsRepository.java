package com.marketingagent.domain.ads.autopilot;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopilotSettingsRepository extends JpaRepository<AutopilotSettings, Long> {

    Optional<AutopilotSettings> findTopByOrderByIdAsc();
}
