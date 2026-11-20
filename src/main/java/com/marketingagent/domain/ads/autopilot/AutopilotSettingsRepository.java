package com.marketingagent.domain.ads.autopilot;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopilotSettingsRepository extends JpaRepository<AutopilotSettings, Long> {

    Optional<AutopilotSettings> findByAdAccountId(Long adAccountId);

    /** 자동 운영을 켜 둔 계정들. 일 배치가 차례로 돈다. */
    List<AutopilotSettings> findByEnabledTrue();

    void deleteByAdAccountId(Long adAccountId);
}
