package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.HourlyConversionAggregate;
import com.marketingagent.domain.ads.dto.KeywordConversionAggregate;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordConversionHourlyRepository extends JpaRepository<KeywordConversionHourly, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from KeywordConversionHourly c where c.adAccountId = :accountId and c.statDate = :date")
    int deleteByStatDate(@Param("accountId") Long accountId, @Param("date") LocalDate date);

    long countByAdAccountIdAndStatDate(Long adAccountId, LocalDate statDate);

    @Query("""
            select new com.marketingagent.domain.ads.dto.HourlyConversionAggregate(
                c.statHour, c.device, sum(c.convCnt), sum(c.convAmt))
            from KeywordConversionHourly c
            where c.adAccountId = :accountId and c.statDate between :since and :until
            group by c.statHour, c.device
            """)
    List<HourlyConversionAggregate> aggregateByHour(@Param("accountId") Long accountId, @Param("since") LocalDate since, @Param("until") LocalDate until);

    @Query("""
            select new com.marketingagent.domain.ads.dto.KeywordConversionAggregate(
                c.nccKeywordId, c.convMethod, c.convType, sum(c.convCnt), sum(c.convAmt))
            from KeywordConversionHourly c
            where c.adAccountId = :accountId and c.statDate between :since and :until
            group by c.nccKeywordId, c.convMethod, c.convType
            """)
    List<KeywordConversionAggregate> aggregateByKeyword(
            @Param("accountId") Long accountId, @Param("since") LocalDate since, @Param("until") LocalDate until);

    @Modifying
    @Query("delete from KeywordConversionHourly x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);
}
