package com.marketingagent.domain.ads.planner;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.planner.PlannerDtos.Briefing;
import com.marketingagent.domain.ads.planner.PlannerDtos.BriefingPeriod;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "Planner", description = "목표 기반 운영 계획 · 일간/주간 브리핑")
@SecurityRequirement(name = "bearerAuth")
public class PlannerController {

    private final PlanService planService;
    private final BriefingService briefingService;

    @Operation(summary = "운영 계획 제안 받기",
            description = "월 목표 전환수 또는 월 예산을 주면 키워드·예산·기대 성과·운영 방법을 제안하고 현재 계획으로 저장한다. "
                    + "네이버 계정에는 아무것도 쓰지 않는다.")
    @PostMapping("/plans/propose")
    public ResponseEntity<ApiResponse<PlanProposal>> propose(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(planService.propose(request)));
    }

    @Operation(summary = "현재 운영 계획", description = "받아 둔 계획이 없으면 data 가 null 이다.")
    @GetMapping("/plans/current")
    public ResponseEntity<ApiResponse<PlanProposal>> currentPlan() {
        return ResponseEntity.ok(ApiResponse.ok(planService.current().orElse(null)));
    }

    @Operation(summary = "브리핑 만들기",
            description = "period 는 DAILY(어제) 또는 WEEKLY(최근 7일). 같은 기간을 다시 만들면 덮어쓴다.")
    @PostMapping("/briefings")
    public ResponseEntity<ApiResponse<Briefing>> generate(@RequestParam(defaultValue = "WEEKLY") BriefingPeriod period) {
        return ResponseEntity.ok(ApiResponse.ok(briefingService.generate(period)));
    }

    @Operation(summary = "최근 브리핑 목록")
    @GetMapping("/briefings")
    public ResponseEntity<ApiResponse<List<Briefing>>> briefings() {
        return ResponseEntity.ok(ApiResponse.ok(briefingService.recent()));
    }
}
