package com.marketingagent.domain.ads;

import com.marketingagent.integration.naver.NaverSearchAdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** naver.searchad.sync-enabled=true 일 때만 동작하는 일 배치. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naver.searchad.sync-enabled", havingValue = "true")
public class AdSyncScheduler {

    private final AdSyncService adSyncService;
    private final NaverSearchAdProperties properties;

    @Scheduled(cron = "${naver.searchad.sync-cron}")
    public void syncDaily() {
        try {
            adSyncService.sync(properties.syncDays());
        } catch (Exception e) {
            log.error("검색광고 일 배치 동기화 실패", e);
        }
    }
}
