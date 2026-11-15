package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.DailyPoint;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordStatDailyRepository extends JpaRepository<KeywordStatDaily, Long> {

    Optional<KeywordStatDaily> findByAdAccountIdAndNccKeywordIdAndStatDate(
            Long adAccountId, String nccKeywordId, LocalDate statDate);

    @Query("""
            select new com.marketingagent.domain.ads.dto.KeywordAggregate(
                s.nccKeywordId, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt), sum(s.ccnt), sum(s.avgRnk * s.impCnt))
            from KeywordStatDaily s
            where s.adAccountId = :accountId
              and s.statDate between :since and :until
            group by s.nccKeywordId
            """)
    List<KeywordAggregate> aggregateByKeyword(@Param("accountId") Long accountId,
            @Param("since") LocalDate since, @Param("until") LocalDate until);

    @Query("""
            select new com.marketingagent.domain.ads.dto.DailyPoint(
                s.statDate, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt), sum(s.ccnt))
            from KeywordStatDaily s
            where s.adAccountId = :accountId
              and s.statDate between :since and :until
            group by s.statDate
            order by s.statDate
            """)
    List<DailyPoint> aggregateByDate(@Param("accountId") Long accountId,
            @Param("since") LocalDate since, @Param("until") LocalDate until);

    /** 캠페인 하나에 속한 키워드만 일자별로 합산한다. 키워드 -> 광고그룹 -> 캠페인으로 거슬러 올라간다. */
    @Query("""
            select new com.marketingagent.domain.ads.dto.DailyPoint(
                s.statDate, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt), sum(s.ccnt))
            from KeywordStatDaily s
            where s.adAccountId = :accountId
              and s.statDate between :since and :until
              and s.nccKeywordId in (
                  select k.nccKeywordId from AdKeyword k, AdGroup g
                  where k.adAccountId = :accountId and g.adAccountId = :accountId
                    and k.nccAdgroupId = g.nccAdgroupId and g.nccCampaignId = :campaignId)
            group by s.statDate
            order by s.statDate
            """)
    List<DailyPoint> aggregateByDateForCampaign(@Param("accountId") Long accountId,
            @Param("campaignId") String campaignId,
            @Param("since") LocalDate since, @Param("until") LocalDate until);

    @Modifying
    @Query("delete from KeywordStatDaily s where s.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update KeywordStatDaily s set s.adAccountId = :accountId where s.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
