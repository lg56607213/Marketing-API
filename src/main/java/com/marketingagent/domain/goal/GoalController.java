package com.marketingagent.domain.goal;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.goal.GoalDtos.CheckinRequest;
import com.marketingagent.domain.goal.GoalDtos.CheckinResult;
import com.marketingagent.domain.goal.GoalDtos.ConfirmRequest;
import com.marketingagent.domain.goal.GoalDtos.GoalView;
import com.marketingagent.domain.goal.GoalDtos.MessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목표 상담. 모든 요청은 accountId 로 광고 계정을 지정한다. 계정마다 사업 구조와 목표가 따로 있다.
 */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Tag(name = "Goals", description = "대화로 사업 구조를 파악하고 목표 CPA·예산을 정한다")
@SecurityRequirement(name = "bearerAuth")
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "지금 상담", description = "가장 최근 상담. 없으면 data 가 null.")
    @GetMapping
    public ResponseEntity<ApiResponse<GoalView>> current(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.current(accountId)));
    }

    @Operation(summary = "새 상담 시작", description = "전에 상담했으면 사업 구조는 이어받고 목표만 다시 묻는다.")
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<GoalView>> start(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.start(accountId)));
    }

    @Operation(summary = "말 보내기", description = "답을 읽어 사업 구조를 채우고 다음 질문을 돌려준다. 다 채워지면 계산과 운영안까지 만든다.")
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<GoalView>> reply(@RequestParam Long accountId,
            @Valid @RequestBody MessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.reply(accountId, request.text())));
    }

    @Operation(summary = "사업 구조 직접 고치기", description = "화면 패널에서 고친 값. 범위를 벗어난 값은 버린다.")
    @PutMapping("/model")
    public ResponseEntity<ApiResponse<GoalView>> updateModel(@RequestParam Long accountId,
            @RequestBody BusinessModel model) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.updateModel(accountId, model)));
    }

    @Operation(summary = "다시 검토", description = "같은 사업 구조로 최근 실적과 네이버 예측을 다시 반영한다.")
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<GoalView>> analyze(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.reanalyze(accountId)));
    }

    @Operation(summary = "안 확정", description = "운영 계획을 저장하고 자동 운영·입찰 추천의 목표 CPA 와 월 한도를 맞춘다.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<GoalView>> confirm(@RequestParam Long accountId,
            @Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.confirm(accountId, request)));
    }

    @Operation(summary = "실제 결과 알려 주기", description = "기간 동안의 문의·계약 수. 문의를 비우면 네이버 전환 수를 쓴다.")
    @PostMapping("/checkins")
    public ResponseEntity<ApiResponse<CheckinResult>> checkin(@RequestParam Long accountId,
            @Valid @RequestBody CheckinRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.checkin(accountId, request)));
    }

    @Operation(summary = "새 목표 CPA 적용", description = "실제 결과로 다시 계산한 목표 CPA 를 자동 운영과 입찰 추천에 쓴다.")
    @PostMapping("/target/apply")
    public ResponseEntity<ApiResponse<GoalView>> applyTarget(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(goalService.applyTarget(accountId)));
    }
}
