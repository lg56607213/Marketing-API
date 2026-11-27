package com.marketingagent.domain.subsidy;

import com.marketingagent.domain.subsidy.collect.AnnouncementCollector;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 키를 새로 넣어 아직 공고가 하나도 없는 소스가 있으면, 아침 배치를 기다리지 않고 한 번 수집·분석·매칭한다.
 * 모든 소스에 공고가 있으면 아무것도 하지 않는다. 서버 기동을 막지 않게 따로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubsidyBootstrap {

    private final SubsidyService service;
    private final SubsidyProperties properties;
    private final SubsidyAnnouncementRepository announcementRepository;
    private final List<AnnouncementCollector> collectors;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        boolean newSource = collectors.stream()
                .anyMatch(c -> c.configured() && announcementRepository.countBySource(c.source()) == 0);
        if (!properties.enabled() || !newSource) {
            return;
        }
        Thread worker = new Thread(() -> {
            log.info("지원사업 첫 수집을 시작합니다(공고가 아직 없는 소스가 있음).");
            service.collect();
            service.analyze();
            service.matchAll();
        }, "subsidy-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }
}
