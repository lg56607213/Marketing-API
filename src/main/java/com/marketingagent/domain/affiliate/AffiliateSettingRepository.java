package com.marketingagent.domain.affiliate;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliateSettingRepository extends JpaRepository<AffiliateSetting, Long> {

    Optional<AffiliateSetting> findByCompanyId(Long companyId);
}
