package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.dto.SearchQueryAggregate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchQueryDailyRepository extends JpaRepository<SearchQueryDaily, Long> {

    Optional<SearchQueryDaily> findByStatDateAndNccAdgroupIdAndSearchQueryAndDevice(
            LocalDate statDate, String nccAdgroupId, String searchQuery, String device);

    @Query("""
            select new com.marketingagent.domain.ads.dto.SearchQueryAggregate(
                s.searchQuery, sum(s.impCnt), sum(s.clkCnt), sum(s.salesAmt))
            from SearchQueryDaily s
            where s.statDate between :since and :until
            group by s.searchQuery
            order by sum(s.salesAmt) desc, sum(s.impCnt) desc
            """)
    List<SearchQueryAggregate> aggregate(@Param("since") LocalDate since, @Param("until") LocalDate until);
}
