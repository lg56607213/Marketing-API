package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.dto.AdReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads/reports")
@RequiredArgsConstructor
@Tag(name = "AdReports", description = "광고 성과 분석 리포트")
@SecurityRequirement(name = "bearerAuth")
public class AdReportController {

    private final AdReportService adReportService;

    @Operation(summary = "리포트 생성", description = "기간 성과를 분석해 리포트를 만든다. AI 미설정 시 규칙 기반으로 생성한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AdReportResponse>> generate(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(AdReportResponse.from(adReportService.generate(days))));
    }

    @Operation(summary = "최근 리포트 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdReportResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(adReportService.findRecent().stream()
                .map(AdReportResponse::from)
                .toList()));
    }

    @Operation(summary = "리포트 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdReportResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(AdReportResponse.from(adReportService.findById(id))));
    }
}
