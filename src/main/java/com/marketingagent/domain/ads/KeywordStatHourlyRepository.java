package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.HourlyAggregate;
import com.marketingagent.domain.ads.dto.KeywordCostAggregate;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordStatHourlyRepository extends JpaRepository<KeywordStatHourly, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from KeywordStatHourly s where s.statDate = :date")
    int deleteByStatDate(@Param("date") LocalDate date);

    long countByStatDate(LocalDate statDate);

    @Query("""
            select new com.marketingagent.domain.ads.dto.HourlyAggregate(
                s.statHour, s.device, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt), sum(s.rnkSum))
            from KeywordStatHourly s
            where s.statDate between :since and :until
            group by s.statHour, s.device
            """)
    List<HourlyAggregate> aggregateByHour(@Param("since") LocalDate since, @Param("until") LocalDate until);

    @Query("""
            select new com.marketingagent.domain.ads.dto.KeywordCostAggregate(
                s.nccKeywordId, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt))
            from KeywordStatHourly s
            where s.statDate between :since and :until
            group by s.nccKeywordId
            """)
    List<KeywordCostAggregate> aggregateByKeyword(@Param("since") LocalDate since, @Param("until") LocalDate until);
}
