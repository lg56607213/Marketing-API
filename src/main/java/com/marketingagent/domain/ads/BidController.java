package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.ads.dto.BidApplyResult;
import com.marketingagent.domain.ads.dto.BidRecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입찰가 조정. 추천 생성은 읽기 전용이고, 실제 반영은 승인 엔드포인트에서만 일어난다.
 */
@RestController
@RequestMapping("/api/ads/bids")
@RequiredArgsConstructor
@Tag(name = "Bids", description = "입찰가 조정 추천 및 승인")
@SecurityRequirement(name = "bearerAuth")
public class BidController {

    private final BidRecommendationService bidRecommendationService;

    @Operation(summary = "입찰가 추천 생성", description = "최근 성과를 분석해 추천을 만든다. 네이버에는 아무것도 반영하지 않는다.")
    @PostMapping("/recommend")
    public ResponseEntity<ApiResponse<List<BidRecommendationResponse>>> recommend() {
        List<BidRecommendationResponse> result = bidRecommendationService.generate().stream()
                .map(BidRecommendationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "목표 노출순위 기준 추천 생성",
            description = "네이버 추정 입찰가로 목표 평균노출순위를 맞춘다. 네이버에는 아무것도 반영하지 않는다.")
    @PostMapping("/target-rank")
    public ResponseEntity<ApiResponse<List<BidRecommendationResponse>>> targetRank(
            @RequestParam(defaultValue = "3") int position,
            @RequestParam(defaultValue = "MOBILE") String device) {
        List<BidRecommendationResponse> result =
                bidRecommendationService.generateForTargetRank(position, device).stream()
                        .map(BidRecommendationResponse::from)
                        .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "승인 대기 추천 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BidRecommendationResponse>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(bidRecommendationService.findPending().stream()
                .map(BidRecommendationResponse::from)
                .toList()));
    }

    @Operation(summary = "처리 이력 조회", description = "반영·거절·실패 건의 감사 기록")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<BidRecommendationResponse>>> history() {
        return ResponseEntity.ok(ApiResponse.ok(bidRecommendationService.findHistory().stream()
                .map(BidRecommendationResponse::from)
                .toList()));
    }

    @Operation(summary = "추천 승인 및 반영", description = "승인한 건만 네이버 검색광고에 실제로 반영한다.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<BidApplyResult>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(bidRecommendationService.approve(id, CurrentUser.id())));
    }

    @Operation(summary = "추천 거절")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BidApplyResult>> reject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(bidRecommendationService.reject(id, CurrentUser.id())));
    }

    @Operation(summary = "선택 건 일괄 승인 및 반영")
    @PostMapping("/approve")
    public ResponseEntity<ApiResponse<List<BidApplyResult>>> approveAll(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.ok(bidRecommendationService.approveAll(ids, CurrentUser.id())));
    }
}
