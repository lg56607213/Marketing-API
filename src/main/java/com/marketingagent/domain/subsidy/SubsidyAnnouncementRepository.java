package com.marketingagent.domain.subsidy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubsidyAnnouncementRepository extends JpaRepository<SubsidyAnnouncement, Long> {
    Optional<SubsidyAnnouncement> findBySourceAndSourceId(String source, String sourceId);

    /** 아직 분석하지 않았고 마감 전인 공고. 마감이 가까운 것부터, 마감일 모르는 것은 뒤로. */
    @Query("select a from SubsidyAnnouncement a where a.analyzedAt is null and (a.applyEnd is null or a.applyEnd >= :today) "
            + "order by case when a.applyEnd is null then 1 else 0 end, a.applyEnd asc")
    List<SubsidyAnnouncement> findUnanalyzed(@Param("today") LocalDate today, Pageable page);

    /** 매칭 대상: 마감 전이거나 마감일을 모르는 공고. */
    @Query("select a from SubsidyAnnouncement a where a.applyEnd is null or a.applyEnd >= :today")
    List<SubsidyAnnouncement> findOpen(@Param("today") LocalDate today);

    long countByAnalyzedAtIsNull();
}
