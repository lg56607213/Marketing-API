package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.SearchQueryAggregate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchQueryDailyRepository extends JpaRepository<SearchQueryDaily, Long> {

    Optional<SearchQueryDaily> findByAdAccountIdAndStatDateAndNccAdgroupIdAndSearchQueryAndDevice(
            Long adAccountId, LocalDate statDate, String nccAdgroupId, String searchQuery, String device);

    @Query("""
            select new com.marketingagent.domain.ads.dto.SearchQueryAggregate(
                s.searchQuery, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt))
            from SearchQueryDaily s
            where s.adAccountId = :accountId
              and s.statDate between :since and :until
            group by s.searchQuery
            order by sum(s.salesAmt) desc, sum(s.impCnt) desc
            """)
    List<SearchQueryAggregate> aggregate(@Param("accountId") Long accountId,
            @Param("since") LocalDate since, @Param("until") LocalDate until);

    @Query("""
            select new com.marketingagent.domain.ads.dto.SearchQueryAggregate(
                s.searchQuery, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt))
            from SearchQueryDaily s
            where s.adAccountId = :accountId
              and s.nccCampaignId = :campaignId
              and s.statDate between :since and :until
            group by s.searchQuery
            order by sum(s.salesAmt) desc, sum(s.impCnt) desc
            """)
    List<SearchQueryAggregate> aggregateForCampaign(@Param("accountId") Long accountId,
            @Param("campaignId") String campaignId,
            @Param("since") LocalDate since, @Param("until") LocalDate until);

    @Modifying
    @Query("delete from SearchQueryDaily s where s.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update SearchQueryDaily s set s.adAccountId = :accountId where s.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
