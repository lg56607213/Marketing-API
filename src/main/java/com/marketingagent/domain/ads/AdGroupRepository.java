package com.marketingagent.domain.ads;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdGroupRepository extends JpaRepository<AdGroup, Long> {
    Optional<AdGroup> findByNccAdgroupId(String nccAdgroupId);
    List<AdGroup> findByNccCampaignId(String nccCampaignId);
}
