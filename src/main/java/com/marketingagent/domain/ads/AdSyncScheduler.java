package com.marketingagent.domain.ads;

import com.marketingagent.common.security.OwnerContext;
import com.marketingagent.domain.ads.account.NaverAccountService;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.domain.ads.planner.BriefingService;
import com.marketingagent.domain.ads.planner.PlannerDtos.BriefingPeriod;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * naver.searchad.sync-enabled=true 일 때만 동작하는 일 배치.
 * 네이버 계정을 연결한 회원마다 계정 구조·일자별 성과, 시간대별 성과·전환, 검색어 리포트를 차례로 받고
 * 자동 운영을 켠 회원은 입찰가·키워드·일예산을 조정한 뒤, 일간 브리핑(월요일에는 주간, 1일에는 월간도)을 만든다.
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
    private final BriefingService briefingService;
    private final AutopilotService autopilotService;
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
        // 자동 운영을 켜 둔 회원은 새로 받은 실적으로 입찰가·키워드·일예산을 조정한다. 브리핑보다 먼저 돌려야 한 일이 보고에 들어간다.
        try {
            autopilotService.runIfEnabled();
        } catch (Exception e) {
            log.error("자동 운영 실패: user={}", userId, e);
        }
        // 수집이 끝나면 보고서를 써 둔다. 주간 브리핑은 월요일에, 월간 보고서는 매월 1일에 만든다.
        try {
            briefingService.generate(BriefingPeriod.DAILY);
            if (LocalDate.now().getDayOfMonth() == 1) {
                briefingService.generate(BriefingPeriod.MONTHLY);
            }
            if (LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY) {
                briefingService.generate(BriefingPeriod.WEEKLY);
            }
        } catch (Exception e) {
            log.error("브리핑 생성 실패: user={} {}", userId, e.getMessage());
        }
    }
}
