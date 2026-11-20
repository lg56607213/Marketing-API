package com.marketingagent.domain.ads;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRecommendationRepository extends JpaRepository<BidRecommendation, Long> {
    List<BidRecommendation> findByAdAccountIdAndStatusOrderByIdDesc(Long adAccountId, BidStatus status);
    List<BidRecommendation> findByAdAccountIdAndStatusInOrderByIdDesc(Long adAccountId, List<BidStatus> statuses);
    long countByAdAccountIdAndStatusAndDecidedAtAfter(Long adAccountId, BidStatus status, LocalDateTime after);
    List<BidRecommendation> findByAdAccountIdAndStatusAndDecidedAtAfter(Long adAccountId, BidStatus status,
            LocalDateTime after);

    @Modifying
    @Query("delete from BidRecommendation x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    @Modifying
    @Query("update BidRecommendation x set x.adAccountId = :accountId where x.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
