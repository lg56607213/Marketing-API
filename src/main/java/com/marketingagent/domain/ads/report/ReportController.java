package com.marketingagent.domain.ads.report;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.report.DailyReportService.Preview;
import com.marketingagent.domain.ads.report.DailyReportService.SettingsRequest;
import com.marketingagent.domain.ads.report.DailyReportService.SettingsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads/report-mail")
@RequiredArgsConstructor
@Tag(name = "ReportMail", description = "매일 아침 광고 보고 메일")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final DailyReportService reportService;

    @Operation(summary = "보고 메일 설정 (받는 사람·사용 여부·메일 서버 상태)")
    @GetMapping
    public ResponseEntity<ApiResponse<SettingsView>> settings(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.settings(accountId)));
    }

    @Operation(summary = "보고 메일 받는 사람 저장")
    @PutMapping
    public ResponseEntity<ApiResponse<SettingsView>> update(@RequestParam Long accountId,
            @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.updateSettings(accountId, request)));
    }

    @Operation(summary = "오늘 보낼 보고 메일 미리보기")
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<Preview>> preview(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.preview(accountId)));
    }

    @Operation(summary = "지금 보내 보기 (저장한 받는 사람에게)")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<List<String>>> send(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.sendNow(accountId)));
    }
}
