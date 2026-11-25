package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.common.security.SystemTask;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 실시간 입찰 배치. 낮 동안 1시간마다 실계정을 돌며 자동 운영·실시간 입찰을 켠 계정의 입찰가를 목표 순위에 맞춘다.
 * 네이버 동기화를 켠 서버(naver.searchad.sync-enabled=true)에서만 동작한다. 한 계정이 실패해도 나머지는 계속한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naver.searchad.sync-enabled", havingValue = "true")
public class RealtimeBidScheduler {

    private final RealtimeBidService realtimeBidService;
    private final AdAccountRepository accountRepository;

    @Scheduled(cron = "${autopilot.realtime-cron:0 10 8-23 * * *}")
    public void adjustHourly() {
        for (AdAccount account : accountRepository.findAll()) {
            if (account.isStub() || !account.hasCredentials()) {
                continue;
            }
            SystemTask.runFor(account.getId(), () -> {
                try {
                    realtimeBidService.runIfEnabled(account.getId());
                } catch (Exception e) {
                    log.error("실시간 입찰 실패: 계정 {}({})", account.getName(), account.getId(), e);
                }
            });
        }
    }
}
