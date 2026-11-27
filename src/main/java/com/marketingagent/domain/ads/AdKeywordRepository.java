package com.marketingagent.domain.ads;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdKeywordRepository extends JpaRepository<AdKeyword, Long> {
    Optional<AdKeyword> findByAdAccountIdAndNccKeywordId(Long adAccountId, String nccKeywordId);
    List<AdKeyword> findByAdAccountId(Long adAccountId);

    @Modifying
    @Query("delete from AdKeyword x where x.adAccountId = :accountId")
    void deleteAllByAccount(@Param("accountId") Long accountId);

    /** 네이버 ID 없이 저장된 키워드(등록 응답이 비정상이던 것). 동기화가 네이버 기준으로 다시 채운다. */
    @Modifying
    @Query("delete from AdKeyword x where x.adAccountId = :accountId and (x.nccKeywordId is null or x.nccKeywordId = '')")
    int deleteWithoutNaverId(@Param("accountId") Long accountId);

    @Modifying
    @Query("update AdKeyword x set x.adAccountId = :accountId where x.adAccountId is null")
    int adoptOrphans(@Param("accountId") Long accountId);
}
