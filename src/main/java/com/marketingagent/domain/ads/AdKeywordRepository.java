package com.marketingagent.domain.ads;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdKeywordRepository extends JpaRepository<AdKeyword, Long> {
    Optional<AdKeyword> findByNccKeywordId(String nccKeywordId);
    List<AdKeyword> findByNccAdgroupId(String nccAdgroupId);
}
