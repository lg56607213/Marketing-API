package com.marketingagent.domain.ads.account;

import com.marketingagent.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 업체가 쓰는 네이버 검색광고 계정 관리. 조회는 업체 구성원 누구나, 변경은 업체 관리자만 한다. */
@RestController
@RequestMapping("/api/ads/accounts")
@RequiredArgsConstructor
@Tag(name = "AdAccounts", description = "네이버 검색광고 계정 등록 및 관리")
@SecurityRequirement(name = "bearerAuth")
public class AdAccountController {

    private final AdAccountService accountService;

    @Operation(summary = "우리 업체의 광고 계정 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdAccountResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.findMine().stream().map(AdAccountResponse::from).toList()));
    }

    @Operation(summary = "광고 계정 등록", description = "provider 를 stub 으로 주면 자격증명 없이 예시 데이터 계정을 만든다.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdAccountResponse>> create(@Valid @RequestBody AdAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(AdAccountResponse.from(accountService.create(request))));
    }

    @Operation(summary = "광고 계정 수정", description = "자격증명 항목은 비워 두면 기존 값을 유지한다.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdAccountResponse>> update(@PathVariable Long id,
            @Valid @RequestBody AdAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(AdAccountResponse.from(accountService.update(id, request))));
    }

    @Operation(summary = "광고 계정 삭제", description = "이 계정으로 쌓은 성과·추천·리포트도 함께 지운다.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
