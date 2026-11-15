package com.marketingagent.domain.ads;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdKeywordRepository extends JpaRepository<AdKeyword, Long> {
    Optional<AdKeyword> findByAdAccountIdAndNccKeywordId(Long adAccountId, String nccKeywordId);
    List<AdKeyword> findByAdAccountId(Long adAccountId);

    @Modifying
    @Query("delete from AdKeyword x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update AdKeyword x set x.adAccountId = :accountId where x.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
