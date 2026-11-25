package com.marketingagent.domain.ads.autopilot;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopilotRunRepository extends JpaRepository<AutopilotRun, Long> {

    List<AutopilotRun> findTop30ByAdAccountIdOrderByIdDesc(Long adAccountId);

    List<AutopilotRun> findByAdAccountIdAndPreviewFalseAndRunDateOrderByIdAsc(Long adAccountId, LocalDate runDate);

    /** 실제로 네이버에 반영한 실행 기록. 키워드 정리가 자기가 손댄 키워드를 찾을 때 쓴다. */
    List<AutopilotRun> findByAdAccountIdAndPreviewFalseAndDryRunFalseAndRunDateBetweenOrderByIdAsc(
            Long adAccountId, LocalDate since, LocalDate until);

    /** 최근 실행 기록. 리포트가 지난 하루 동안 한 일을 모을 때 쓴다. */
    List<AutopilotRun> findByAdAccountIdAndPreviewFalseAndRunDateBetweenOrderByIdAsc(
            Long adAccountId, LocalDate since, LocalDate until);

    void deleteByAdAccountId(Long adAccountId);
}
