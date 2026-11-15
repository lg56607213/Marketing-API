package com.marketingagent.domain.ads;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdGroupRepository extends JpaRepository<AdGroup, Long> {
    Optional<AdGroup> findByAdAccountIdAndNccAdgroupId(Long adAccountId, String nccAdgroupId);
    List<AdGroup> findByAdAccountIdOrderByNameAsc(Long adAccountId);
    List<AdGroup> findByAdAccountIdAndNccCampaignIdOrderByNameAsc(Long adAccountId, String nccCampaignId);

    @Modifying
    @Query("delete from AdGroup x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update AdGroup x set x.adAccountId = :accountId where x.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
