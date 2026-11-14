package com.marketingagent.domain.market;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.market.MarketDtos.CompetitorReport;
import com.marketingagent.domain.market.MarketDtos.CompetitorRequest;
import com.marketingagent.domain.market.MarketDtos.MarketOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Tag(name = "Market", description = "시장(키워드 수요·경쟁) · 경쟁사 홈페이지 비교")
@SecurityRequirement(name = "bearerAuth")
public class MarketController {

    private final MarketService marketService;

    @Operation(summary = "키워드 시장 현황",
            description = "핵심 키워드의 연관 키워드별 월 검색수, 경쟁 정도, 순위별 추정 입찰가. seeds 를 비우면 업체 정보의 키워드를 쓴다.")
    @GetMapping("/keywords")
    public ResponseEntity<ApiResponse<MarketOverview>> keywords(@RequestParam(required = false) String seeds) {
        return ResponseEntity.ok(ApiResponse.ok(marketService.overview(seeds)));
    }

    @Operation(summary = "경쟁사 비교",
            description = "경쟁사 홈페이지를 내 홈페이지와 같은 기준으로 확인하고, 내 키워드의 자리 다툼 강도를 함께 돌려준다. "
                    + "경쟁사의 광고비·입찰가는 네이버가 공개하지 않아 포함하지 않는다.")
    @PostMapping("/competitors")
    public ResponseEntity<ApiResponse<CompetitorReport>> competitors(@Valid @RequestBody CompetitorRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(marketService.competitors(request.urls())));
    }
}
