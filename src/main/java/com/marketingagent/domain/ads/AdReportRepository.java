package com.marketingagent.domain.ads;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdReportRepository extends JpaRepository<AdReport, Long> {
    List<AdReport> findTop20ByAdAccountIdOrderByIdDesc(Long adAccountId);

    @Modifying
    @Query("delete from AdReport x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update AdReport x set x.adAccountId = :accountId where x.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
