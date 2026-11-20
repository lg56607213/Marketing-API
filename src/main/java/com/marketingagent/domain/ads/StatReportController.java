package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.dto.DiagnosticReport;
import com.marketingagent.domain.ads.dto.HourOfDayPerformance;
import com.marketingagent.domain.ads.dto.KeywordConversionPerformance;
import com.marketingagent.domain.ads.dto.ReportSyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "StatReports", description = "시간대별 성과 · 전환 · 연결 점검")
@SecurityRequirement(name = "bearerAuth")
public class StatReportController {

    private static final int DEFAULT_SYNC_DAYS = 7;
    private static final int DEFAULT_RANGE_DAYS = 14;

    private final ReportSyncService reportSyncService;
    private final ReportAnalyticsService reportAnalyticsService;
    private final AdDiagnosticsService diagnosticsService;

    @Operation(summary = "실계정 연결 점검",
            description = "자격증명, 계정 구조 조회, 성과 조회, 리포트 3종의 형식을 차례로 확인한다. "
                    + "광고 설정은 바꾸지 않는다. 리포트를 실제로 만들기 때문에 1~2분 걸릴 수 있다.")
    @GetMapping("/diagnostics")
    public ResponseEntity<ApiResponse<DiagnosticReport>> diagnostics(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(diagnosticsService.run(accountId)));
    }

    @Operation(summary = "시간대별 성과 · 전환 리포트 가져오기",
            description = "전일부터 거꾸로 days 일치를 내려받아 적재한다. 하루에 리포트 2개를 만들므로 시간이 걸린다.")
    @PostMapping("/stat-reports/sync")
    public ResponseEntity<ApiResponse<ReportSyncResult>> sync(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "" + DEFAULT_SYNC_DAYS) int days) {
        return ResponseEntity.ok(ApiResponse.ok(reportSyncService.sync(accountId, days)));
    }

    @Operation(summary = "하루 중 시간대별 성과", description = "device 는 P(PC), M(모바일). 비우면 전체.")
    @GetMapping("/stat-reports/hourly")
    public ResponseEntity<ApiResponse<List<HourOfDayPerformance>>> hourly(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(required = false) String device) {
        LocalDate end = until != null ? until : LocalDate.now().minusDays(1);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(reportAnalyticsService.hourOfDay(accountId, start, end, device)));
    }

    @Operation(summary = "키워드별 비용 대비 전환 성과", description = "전환수, 전환매출액, CPA, ROAS. 광고비 내림차순.")
    @GetMapping("/stat-reports/conversions")
    public ResponseEntity<ApiResponse<List<KeywordConversionPerformance>>> conversions(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        LocalDate end = until != null ? until : LocalDate.now().minusDays(1);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(reportAnalyticsService.keywordConversions(accountId, start, end)));
    }
}
