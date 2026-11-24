package com.marketingagent.domain.user;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.user.dto.SignupSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최고 관리자 전용. 모든 업체의 가입 신청을 승인·거절한다. */
@RestController
@RequestMapping("/api/super-admin/signups")
@RequiredArgsConstructor
@Tag(name = "SuperAdmin", description = "최고 관리자 전용: 가입 승인")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SignupApprovalService signupApprovalService;

    @Operation(summary = "가입한 계정 목록 (승인 대기·이용 중·거절)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SignupSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(signupApprovalService.list()));
    }

    @Operation(summary = "가입 승인 (거절·이용 중지된 계정을 다시 허용할 때도 쓴다)")
    @PostMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<SignupSummary>> approve(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(signupApprovalService.approve(userId)));
    }

    @Operation(summary = "가입 거절 또는 이용 중지")
    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<SignupSummary>> reject(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(signupApprovalService.reject(userId)));
    }
}
