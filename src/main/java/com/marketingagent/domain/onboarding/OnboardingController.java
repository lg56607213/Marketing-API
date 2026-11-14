package com.marketingagent.domain.onboarding;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.onboarding.OnboardingDtos.ProfileRequest;
import com.marketingagent.domain.onboarding.OnboardingDtos.Proposal;
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
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "업체 정보 등록 · 예산 3안 제안")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @Operation(summary = "내 업체 정보와 마지막 제안", description = "아직 등록하지 않았으면 data 가 null 이다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Proposal>> current() {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.current().orElse(null)));
    }

    @Operation(summary = "업체 정보 등록 후 제안 받기",
            description = "업체명·홈페이지·사업 내용·핵심 키워드·월 예산·목표를 받아 홈페이지를 확인하고, "
                    + "증액 / 요청대로 / 감액 세 안의 기대 효과와 추천을 돌려준다. 광고 계정은 바꾸지 않는다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Proposal>> submit(@Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.submit(request)));
    }

    @Operation(summary = "안 선택", description = "option 은 INCREASE, REQUESTED, DECREASE 중 하나. 고른 안이 현재 운영 계획이 된다.")
    @PostMapping("/select")
    public ResponseEntity<ApiResponse<Proposal>> select(@RequestParam String option) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.select(option)));
    }
}
