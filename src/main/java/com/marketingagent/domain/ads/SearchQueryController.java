package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.dto.SearchQueryPerformance;
import com.marketingagent.domain.ads.dto.SearchQuerySyncResult;
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
@RequestMapping("/api/ads/search-queries")
@RequiredArgsConstructor
@Tag(name = "SearchQueries", description = "실제 유입 검색어 리포트")
@SecurityRequirement(name = "bearerAuth")
public class SearchQueryController {

    private static final int DEFAULT_DAYS = 7;

    private final SearchQueryService searchQueryService;

    @Operation(summary = "검색어 리포트 가져오기",
            description = "네이버에서 일자별 검색어 리포트를 내려받아 적재한다. 하루씩 생성되므로 시간이 걸린다.")
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<SearchQuerySyncResult>> sync(
            @RequestParam(defaultValue = "" + DEFAULT_DAYS) int days) {
        return ResponseEntity.ok(ApiResponse.ok(searchQueryService.sync(days)));
    }

    @Operation(summary = "검색어별 성과 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SearchQueryPerformance>>> performance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        LocalDate end = until != null ? until : LocalDate.now();
        LocalDate start = since != null ? since : end.minusDays(DEFAULT_DAYS - 1L);
        return ResponseEntity.ok(ApiResponse.ok(searchQueryService.performance(start, end)));
    }
}
