package com.marketingagent.domain.onboarding;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {

    Optional<BusinessProfile> findByAdAccountId(Long adAccountId);

    void deleteByAdAccountId(Long adAccountId);
}
