package com.marketingagent.domain.ads.autopilot;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.EfficiencyBidService;
import com.marketingagent.domain.ads.EfficiencyBidService.Analysis;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.RunResult;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Settings;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.SettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "Autopilot", description = "자동 운영 · 광고비 대비 효율 계산")
@SecurityRequirement(name = "bearerAuth")
public class AutopilotController {

    private final AutopilotService autopilotService;
    private final EfficiencyBidService efficiencyBidService;
    private final RealtimeBidService realtimeBidService;

    @Operation(summary = "자동 운영 설정 조회")
    @GetMapping("/autopilot")
    public ResponseEntity<ApiResponse<Settings>> settings(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(autopilotService.settings(accountId)));
    }

    @Operation(summary = "자동 운영 설정 변경",
            description = "enabled=true 로 두면 일 배치가 수집 뒤에 입찰가·키워드 중지·일예산을 한도 안에서 직접 바꾼다.")
    @PutMapping("/autopilot")
    public ResponseEntity<ApiResponse<Settings>> update(@RequestParam Long accountId, @Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(autopilotService.updateSettings(accountId, request)));
    }

    @Operation(summary = "일임 범위 변경 (키워드 추가·제외·정리, 실시간 입찰, 지켜보기 기간)")
    @PutMapping("/autopilot/delegation")
    public ResponseEntity<ApiResponse<Settings>> updateDelegation(@RequestParam Long accountId,
            @Valid @RequestBody AutopilotDtos.DelegationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(autopilotService.updateDelegation(accountId, request)));
    }

    @Operation(summary = "실시간 입찰 지금 실행 (preview=true 면 계산만)")
    @PostMapping("/autopilot/realtime")
    public ResponseEntity<ApiResponse<RunResult>> realtime(@RequestParam Long accountId,
            @RequestParam(defaultValue = "true") boolean preview) {
        return ResponseEntity.ok(ApiResponse.ok(realtimeBidService.run(accountId, preview)));
    }

    @Operation(summary = "자동 운영 실행",
            description = "preview=true(기본)면 무엇을 할지만 계산하고 아무것도 바꾸지 않는다. "
                    + "preview=false 는 자동 운영이 켜져 있을 때만 되고, 광고 계정을 실제로 바꾼다.")
    @PostMapping("/autopilot/run")
    public ResponseEntity<ApiResponse<RunResult>> run(@RequestParam Long accountId, @RequestParam(defaultValue = "true") boolean preview) {
        return ResponseEntity.ok(ApiResponse.ok(autopilotService.run(accountId, preview)));
    }

    @Operation(summary = "자동 운영 기록", description = "무엇을 왜 바꿨는지의 기록. 미리보기도 포함한다.")
    @GetMapping("/autopilot/runs")
    public ResponseEntity<ApiResponse<List<RunResult>>> runs(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(autopilotService.recentRuns(accountId)));
    }

    @Operation(summary = "키워드별 광고비 대비 효율",
            description = "최근 28일 실적으로 키워드마다 클릭당 쓸 수 있는 돈과 실제 클릭 단가, 적정 입찰가를 계산한다. 아무것도 바꾸지 않는다.")
    @GetMapping("/efficiency")
    public ResponseEntity<ApiResponse<Analysis>> efficiency(@RequestParam Long accountId) {
        return ResponseEntity.ok(ApiResponse.ok(efficiencyBidService.analyze(accountId, autopilotService.targets(accountId))));
    }
}
