package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.DailyPoint;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordStatDailyRepository extends JpaRepository<KeywordStatDaily, Long> {

    Optional<KeywordStatDaily> findByNccKeywordIdAndStatDate(String nccKeywordId, LocalDate statDate);

    List<KeywordStatDaily> findByNccKeywordIdInAndStatDateBetween(
            List<String> nccKeywordIds, LocalDate since, LocalDate until);

    @Query("""
            select new com.marketingagent.domain.ads.dto.KeywordAggregate(
                s.nccKeywordId, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt), sum(s.ccnt), sum(s.avgRnk * s.impCnt))
            from KeywordStatDaily s
            where s.statDate between :since and :until
            group by s.nccKeywordId
            """)
    List<KeywordAggregate> aggregateByKeyword(@Param("since") LocalDate since, @Param("until") LocalDate until);

    @Query("""
            select new com.marketingagent.domain.ads.dto.DailyPoint(
                s.statDate, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt), sum(s.ccnt))
            from KeywordStatDaily s
            where s.statDate between :since and :until
            group by s.statDate
            order by s.statDate
            """)
    List<DailyPoint> aggregateByDate(@Param("since") LocalDate since, @Param("until") LocalDate until);
}
