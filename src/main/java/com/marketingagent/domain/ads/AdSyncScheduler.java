package com.marketingagent.domain.ads;

import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountRepository;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * naver.searchad.sync-enabled=true 일 때만 동작하는 일 배치.
 * 모든 업체의 실계정을 차례로 동기화하며, 한 계정이 실패해도 나머지는 계속한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naver.searchad.sync-enabled", havingValue = "true")
public class AdSyncScheduler {

    private final AdSyncService adSyncService;
    private final AdAccountRepository accountRepository;
    private final NaverSearchAdProperties properties;

    @Scheduled(cron = "${naver.searchad.sync-cron}")
    public void syncDaily() {
        for (AdAccount account : accountRepository.findAll()) {
            if (account.isStub() || !account.hasCredentials()) {
                continue;
            }
            try {
                adSyncService.syncAccount(account, properties.syncDays());
            } catch (Exception e) {
                log.error("검색광고 일 배치 동기화 실패: 계정 {}({})", account.getName(), account.getId(), e);
            }
        }
    }
}
