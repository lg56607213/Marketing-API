package com.marketingagent.domain.seo;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.integration.google.SearchConsoleClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자연검색(SEO) 성과. 광고 데이터와 달리 비용이 없고 노출·클릭·순위만 본다.
 */
@RestController
@RequestMapping("/api/seo")
@RequiredArgsConstructor
@Tag(name = "SEO", description = "자연검색 성과 (구글 서치콘솔)")
@SecurityRequirement(name = "bearerAuth")
public class SeoController {

    private static final int DEFAULT_DAYS = 28;
    private static final int DEFAULT_LIMIT = 100;

    private final SearchConsoleClient searchConsoleClient;

    @Operation(summary = "서치콘솔 연동 상태 진단")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<SearchConsoleClient.Health>> health() {
        return ResponseEntity.ok(ApiResponse.ok(searchConsoleClient.health()));
    }

    @Operation(summary = "자연검색 성과 조회", description = "dimension 은 query(검색어) 또는 page(페이지)")
    @GetMapping("/search-analytics")
    public ResponseEntity<ApiResponse<List<SearchConsoleClient.SearchConsoleRow>>> searchAnalytics(
            @RequestParam(defaultValue = "query") String dimension,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        // 서치콘솔은 최근 2~3일 데이터가 아직 확정되지 않아 기본 종료일을 3일 전으로 둔다.
        LocalDate end = until != null ? until : LocalDate.now().minusDays(3);
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(
                searchConsoleClient.query(start, end, dimension, limit)));
    }
}
