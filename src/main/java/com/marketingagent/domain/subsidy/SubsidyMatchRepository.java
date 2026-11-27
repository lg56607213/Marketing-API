package com.marketingagent.domain.subsidy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubsidyMatchRepository extends JpaRepository<SubsidyMatch, Long> {
    Optional<SubsidyMatch> findByProfileIdAndAnnouncementId(Long profileId, Long announcementId);

    List<SubsidyMatch> findByProfileId(Long profileId);

    @Modifying
    @Query("delete from SubsidyMatch m where m.profileId = :profileId")
    void deleteByProfile(@Param("profileId") Long profileId);

    @Modifying
    @Query("delete from SubsidyMatch m where m.announcementId = :announcementId")
    void deleteByAnnouncement(@Param("announcementId") Long announcementId);
}
