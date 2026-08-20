package com.marketingagent.domain.ads;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {
    Optional<AdCampaign> findByNccCampaignId(String nccCampaignId);
}
