package com.marketingagent.domain.subsidy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 처음 켰을 때(공고가 하나도 없을 때) 아침 배치를 기다리지 않고 한 번 수집·분석·매칭한다.
 * 이미 공고가 있으면 아무것도 하지 않는다. 서버 기동을 막지 않게 따로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubsidyBootstrap {

    private final SubsidyService service;
    private final SubsidyProperties properties;
    private final SubsidyAnnouncementRepository announcementRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.enabled() || announcementRepository.count() > 0
                || (!properties.hasBizinfo() && !properties.hasKstartup())) {
            return;
        }
        Thread worker = new Thread(() -> {
            log.info("지원사업 첫 수집을 시작합니다(공고가 아직 없음).");
            service.collect();
            service.analyze();
            service.matchAll();
        }, "subsidy-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }
}
