package com.marketingagent.domain.affiliate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliatePostRepository extends JpaRepository<AffiliatePost, Long> {

    List<AffiliatePost> findTop100ByCompanyIdOrderByIdDesc(Long companyId);

    Optional<AffiliatePost> findByIdAndCompanyId(Long id, Long companyId);

    long countByCompanyIdAndCreatedAtAfter(Long companyId, LocalDateTime after);
}
