package com.marketingagent.domain.ads.launch;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.launch.ProposalDtos.ApplyRequest;
import com.marketingagent.domain.ads.launch.ProposalDtos.Proposal;
import com.marketingagent.domain.ads.launch.ProposalDtos.ProposalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads/keyword-proposals")
@RequiredArgsConstructor
@Tag(name = "KeywordProposal", description = "키워드 분석·제안 (검색량·클릭률·입찰가별 예측 → 예산 안 추천 → 고른 것만 반영)")
@SecurityRequirement(name = "bearerAuth")
public class KeywordProposalController {

    private final KeywordProposalService proposalService;

    @Operation(summary = "키워드 분석·제안 만들기", description = "네이버에는 아무것도 바꾸지 않는다.")
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<Proposal>> analyze(@RequestParam Long accountId,
            @RequestBody ProposalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(proposalService.analyze(accountId, request)));
    }

    @Operation(summary = "고른 키워드·입찰가 반영", description = "표에서 고른 키워드만 넣거나 입찰가를 맞춘다.")
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<RunResult>> apply(@RequestParam Long accountId,
            @RequestBody ApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(proposalService.apply(accountId, request)));
    }
}
