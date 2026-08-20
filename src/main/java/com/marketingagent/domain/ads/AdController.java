package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.dto.AdHealth;
import com.marketingagent.domain.ads.dto.AdSummary;
import com.marketingagent.domain.ads.dto.KeywordPerformance;
import com.marketingagent.domain.ads.dto.SyncResult;
import com.marketingagent.integration.naver.NaverSearchAdProperties;
import com.marketingagent.integration.naver.SearchAdClient;
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
@Tag(name = "Ads", description = "네이버 검색광고 성과 분석")
@SecurityRequirement(name = "bearerAuth")
public class AdController {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final AdSyncService adSyncService;
    private final AdAnalyticsService adAnalyticsService;
    private final AdCampaignRepository campaignRepository;
    private final SearchAdClient searchAdClient;
    private final NaverSearchAdProperties naverProperties;

    @Operation(summary = "연동 상태 자가진단", description = "자격증명이 제대로 설정됐는지, 실제로 API 호출이 되는지 확인한다.")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<AdHealth>> health() {
        boolean credentialsSet = notBlank(naverProperties.customerId())
                && notBlank(naverProperties.apiKey())
                && notBlank(naverProperties.secretKey());

        if ("stub".equalsIgnoreCase(naverProperties.provider())) {
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("stub", credentialsSet, true, 1,
                    "Stub 모드입니다. 실제 계정에 연결하려면 NAVER_SEARCHAD_PROVIDER=naver 와 자격증명 3종을 설정하세요.")));
        }

        if (!credentialsSet) {
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("naver", false, false, 0,
                    "자격증명이 비어 있습니다. NAVER_CUSTOMER_ID / NAVER_API_KEY / NAVER_SECRET_KEY 를 확인하세요.")));
        }

        try {
            int count = searchAdClient.listCampaigns().size();
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("naver", true, true, count,
                    "연결 정상입니다. 캠페인 " + count + "개를 조회했습니다.")));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(new AdHealth("naver", true, false, 0,
                    "API 호출에 실패했습니다: " + e.getMessage())));
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Operation(summary = "검색광고 데이터 동기화", description = "캠페인/광고그룹/키워드와 일자별 성과를 내려받아 적재한다.")
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SyncResult>> sync(
            @RequestParam(defaultValue = "" + DEFAULT_RANGE_DAYS) int days) {
        return ResponseEntity.ok(ApiResponse.ok(adSyncService.sync(days)));
    }

    @Operation(summary = "캠페인 목록 조회")
    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<List<AdCampaign>>> campaigns() {
        return ResponseEntity.ok(ApiResponse.ok(campaignRepository.findAll()));
    }

    @Operation(summary = "키워드별 성과 조회")
    @GetMapping("/keywords")
    public ResponseEntity<ApiResponse<List<KeywordPerformance>>> keywords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        LocalDate end = until != null ? until : LocalDate.now().minusDays(1);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(adAnalyticsService.keywordPerformance(start, end)));
    }

    @Operation(summary = "기간 성과 요약 조회")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdSummary>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        LocalDate end = until != null ? until : LocalDate.now().minusDays(1);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(adAnalyticsService.summary(start, end)));
    }
}
