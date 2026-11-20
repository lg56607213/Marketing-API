package com.marketingagent.domain.ads.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingRepository extends JpaRepository<BriefingRecord, Long> {

    Optional<BriefingRecord> findByAdAccountIdAndPeriodAndUntil(Long adAccountId, String period, LocalDate until);

    List<BriefingRecord> findTop30ByAdAccountIdOrderByUntilDescIdDesc(Long adAccountId);

    void deleteByAdAccountId(Long adAccountId);
}
