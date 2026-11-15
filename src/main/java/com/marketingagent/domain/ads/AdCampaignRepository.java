package com.marketingagent.domain.ads;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {
    Optional<AdCampaign> findByAdAccountIdAndNccCampaignId(Long adAccountId, String nccCampaignId);
    List<AdCampaign> findByAdAccountIdOrderByNameAsc(Long adAccountId);

    @Modifying
    @Query("delete from AdCampaign x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update AdCampaign x set x.adAccountId = :accountId where x.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
