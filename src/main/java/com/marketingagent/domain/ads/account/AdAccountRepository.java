package com.marketingagent.domain.ads.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdAccountRepository extends JpaRepository<AdAccount, Long> {
    List<AdAccount> findByCompanyIdOrderByIdAsc(Long companyId);
    boolean existsByCompanyIdAndName(Long companyId, String name);
}
