package com.marketingagent.domain.subsidy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubsidyProfileRepository extends JpaRepository<SubsidyProfile, Long> {
    List<SubsidyProfile> findByCompanyIdOrderByIdAsc(Long companyId);

    Optional<SubsidyProfile> findByIdAndCompanyId(Long id, Long companyId);
}
