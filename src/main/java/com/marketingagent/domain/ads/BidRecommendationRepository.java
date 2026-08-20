package com.marketingagent.domain.ads;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRecommendationRepository extends JpaRepository<BidRecommendation, Long> {
    List<BidRecommendation> findByStatusOrderByIdDesc(BidStatus status);
    List<BidRecommendation> findByStatusInOrderByIdDesc(List<BidStatus> statuses);
    long countByStatusAndDecidedAtAfter(BidStatus status, LocalDateTime after);
}
