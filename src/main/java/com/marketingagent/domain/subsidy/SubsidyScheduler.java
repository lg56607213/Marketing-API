package com.marketingagent.domain.subsidy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 08:30 수집·분석, 09:00 매칭·알림 (Asia/Seoul). SUBSIDY_ENABLED=true 일 때만 돈다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubsidyScheduler {

    private final SubsidyService service;
    private final SubsidyProperties properties;

    @Scheduled(cron = "${subsidy.collect-cron:0 30 8 * * *}", zone = "Asia/Seoul")
    public void collectAndAnalyze() {
        if (!properties.enabled()) {
            return;
        }
        service.collect();
        service.analyze();
    }

    @Scheduled(cron = "${subsidy.notify-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void matchAndNotify() {
        if (!properties.enabled()) {
            return;
        }
        service.matchAll();
        service.sendNotifications();
    }
}
