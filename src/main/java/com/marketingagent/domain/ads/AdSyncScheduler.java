package com.marketingagent.domain.ads;

import com.marketingagent.common.security.SystemTask;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountRepository;
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
 *
 * <p>모든 업체의 실계정을 차례로 돈다. 계정마다 계정 구조·일자별 성과, 시간대별 성과·전환, 검색어 리포트를 받고,
 * 자동 운영을 켠 계정은 입찰가·키워드·일예산을 조정한 뒤 일간 브리핑(월요일에는 주간, 1일에는 월간도)을 만든다.
 * 한 계정이나 한 단계가 실패해도 나머지는 계속한다.
 *
 * <p>배치에는 로그인한 사용자가 없으므로 계정마다 {@link SystemTask} 범위를 열고 그 안에서만 그 계정을 다룬다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naver.searchad.sync-enabled", havingValue = "true")
public class AdSyncScheduler {

    private final AdSyncService adSyncService;
    private final ReportSyncService reportSyncService;
    private final SearchQueryService searchQueryService;
    private final AutopilotService autopilotService;
    private final BriefingService briefingService;
    private final com.marketingagent.domain.ads.report.DailyReportService dailyReportService;
    private final AdAccountRepository accountRepository;
    private final NaverSearchAdProperties properties;

    @Scheduled(cron = "${naver.searchad.sync-cron}")
    public void syncDaily() {
        for (AdAccount account : accountRepository.findAll()) {
            if (account.isStub() || !account.hasCredentials()) {
                continue;
            }
            SystemTask.runFor(account.getId(), () -> syncOne(account));
        }
    }

    private void syncOne(AdAccount account) {
        Long id = account.getId();
        String label = account.getName() + "(" + id + ")";
        try {
            adSyncService.syncAccount(account, properties.syncDays());
        } catch (Exception e) {
            log.error("검색광고 일 배치 동기화 실패: 계정 {}", label, e);
        }
        try {
            reportSyncService.sync(id, properties.reportSyncDays());
        } catch (Exception e) {
            log.error("시간대별 성과·전환 리포트 일 배치 실패: 계정 {}", label, e);
        }
        try {
            searchQueryService.sync(id, properties.reportSyncDays());
        } catch (Exception e) {
            log.error("검색어 리포트 일 배치 실패: 계정 {}", label, e);
        }
        // 자동 운영을 켜 둔 계정은 새로 받은 실적으로 입찰가·키워드·일예산을 조정한다. 브리핑보다 먼저 돌려야 한 일이 보고에 들어간다.
        try {
            autopilotService.runIfEnabled(id);
        } catch (Exception e) {
            log.error("자동 운영 실패: 계정 {}", label, e);
        }
        // 수집이 끝나면 보고서를 써 둔다. 주간 브리핑은 월요일에, 월간 보고서는 매월 1일에 만든다.
        try {
            briefingService.generate(id, BriefingPeriod.DAILY);
            if (LocalDate.now().getDayOfMonth() == 1) {
                briefingService.generate(id, BriefingPeriod.MONTHLY);
            }
            if (LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY) {
                briefingService.generate(id, BriefingPeriod.WEEKLY);
            }
        } catch (Exception e) {
            log.error("브리핑 생성 실패: 계정 {} {}", label, e.getMessage());
        }
        // 마지막으로 대표에게 아침 보고 메일을 보낸다. 브리핑과 자동 운영 결과가 메일에 들어간다.
        try {
            dailyReportService.sendIfDue(id);
        } catch (Exception e) {
            log.error("보고 메일 발송 실패: 계정 {} {}", label, e.getMessage());
        }
    }
}
