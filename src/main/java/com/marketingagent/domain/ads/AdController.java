package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.account.AdAccount;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.dto.AdHealth;
import com.marketingagent.domain.ads.dto.AdSummary;
import com.marketingagent.domain.ads.dto.KeywordPerformance;
import com.marketingagent.domain.ads.dto.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색광고 성과 조회. 모든 요청은 accountId 로 광고 계정을 지정하며,
 * campaignId 를 함께 주면 그 캠페인만 본다.
 */
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "Ads", description = "네이버 검색광고 성과 분석")
@SecurityRequirement(name = "bearerAuth")
public class AdController {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final AdSyncService adSyncService;
    private final AdAnalyticsService adAnalyticsService;
    private final AdAccountService accountService;

    @Operation(summary = "연동 상태 자가진단", description = "계정의 자격증명이 채워졌는지, 실제로 API 호출이 되는지 확인한다.")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<AdHealth>> health(@RequestParam Long accountId) {
        AdAccount account = accountService.require(accountId);

        if (account.isStub()) {
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("stub", true, true, 1,
                    "예시 데이터 계정입니다. 실제 광고를 보려면 네이버 자격증명으로 광고 계정을 등록하세요.")));
        }

        if (!account.hasCredentials()) {
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("naver", false, false, 0,
                    "자격증명이 비어 있습니다. 광고 계정 설정에서 고객 ID, 액세스 라이선스, 비밀키를 입력하세요.")));
        }

        try {
            int count = accountService.client(account).listCampaigns().size();
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("naver", true, true, count,
                    "연결 정상입니다. 캠페인 " + count + "개를 조회했습니다.")));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("naver", true, false, 0,
                    "API 호출에 실패했습니다: " + e.getMessage())));
        }
    }

    @Operation(summary = "검색광고 데이터 동기화", description = "캠페인/광고그룹/키워드와 일자별 성과를 내려받아 적재한다.")
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncResult>> sync(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "" + DEFAULT_RANGE_DAYS) int days) {
        return ResponseEntity.ok(ApiResponse.ok(adSyncService.sync(accountId, days)));
    }

    @Operation(summary = "캠페인 목록 조회")
    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<List<AdCampaign>>> campaigns(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(adAnalyticsService.campaigns(accountId)));
    }

    @Operation(summary = "캠페인 목표 CPA 설정",
            description = "입찰가 추천이 이 캠페인의 키워드를 평가할 때 쓰는 기준. 0 이면 지정을 해제한다.")
    @PutMapping("/campaigns/{nccCampaignId}/target-cpa")
    public ResponseEntity<ApiResponse<AdCampaign>> changeTargetCpa(
            @PathVariable String nccCampaignId,
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "0") long targetCpa) {
        return ResponseEntity.ok(ApiResponse.ok(
                adAnalyticsService.changeTargetCpa(accountId, nccCampaignId, targetCpa)));
    }

    @Operation(summary = "광고그룹 목록 조회")
    @GetMapping("/adgroups")
    public ResponseEntity<ApiResponse<List<AdGroup>>> adgroups(
            @RequestParam Long accountId,
            @RequestParam(required = false) String campaignId) {
        return ResponseEntity.ok(ApiResponse.ok(adAnalyticsService.adgroups(accountId, campaignId)));
    }

    @Operation(summary = "키워드별 성과 조회")
    @GetMapping("/keywords")
    public ResponseEntity<ApiResponse<List<KeywordPerformance>>> keywords(
            @RequestParam Long accountId,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        LocalDate end = until != null ? until : LocalDate.now().minusDays(1);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(
                adAnalyticsService.keywordPerformance(accountId, campaignId, start, end)));
    }

    @Operation(summary = "기간 성과 요약 조회")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdSummary>> summary(
            @RequestParam Long accountId,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        LocalDate end = until != null ? until : LocalDate.now().minusDays(1);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(adAnalyticsService.summary(accountId, campaignId, start, end)));
    }
}
