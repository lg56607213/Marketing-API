package com.marketingagent.domain.ads;

import com.marketingagent.common.security.OwnerContext;
import com.marketingagent.domain.ads.account.NaverAccountService;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * naver.searchad.sync-enabled=true 일 때만 동작하는 일 배치.
 * 네이버 계정을 연결한 회원마다 계정 구조·일자별 성과, 시간대별 성과·전환, 검색어 리포트를 차례로 받는다.
 * 한 회원이나 한 단계가 실패해도 나머지는 계속한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naver.searchad.sync-enabled", havingValue = "true")
public class AdSyncScheduler {

    private final AdSyncService adSyncService;
    private final ReportSyncService reportSyncService;
    private final SearchQueryService searchQueryService;
    private final NaverAccountService accountService;
    private final NaverSearchAdProperties properties;

    @Scheduled(cron = "${naver.searchad.sync-cron}")
    public void syncDaily() {
        for (Long userId : accountService.connectedUserIds()) {
            OwnerContext.runAs(userId, () -> syncOne(userId));
        }
    }

    private void syncOne(Long userId) {
        try {
            adSyncService.sync(properties.syncDays());
        } catch (Exception e) {
            log.error("검색광고 일 배치 동기화 실패: user={}", userId, e);
        }
        try {
            reportSyncService.sync(properties.reportSyncDays());
        } catch (Exception e) {
            log.error("시간대별 성과·전환 리포트 일 배치 실패: user={}", userId, e);
        }
        try {
            searchQueryService.sync(properties.reportSyncDays());
        } catch (Exception e) {
            log.error("검색어 리포트 일 배치 실패: user={}", userId, e);
        }
    }
}
