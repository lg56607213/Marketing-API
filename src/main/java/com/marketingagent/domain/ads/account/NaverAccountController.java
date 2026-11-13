package com.marketingagent.domain.ads.account;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.common.security.CurrentUser;
import com.marketingagent.domain.ads.account.NaverAccountDtos.AccountStatus;
import com.marketingagent.domain.ads.account.NaverAccountDtos.ConnectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads/account")
@RequiredArgsConstructor
@Tag(name = "NaverAccount", description = "회원별 네이버 검색광고 계정 연결")
@SecurityRequirement(name = "bearerAuth")
public class NaverAccountController {

    private final NaverAccountService accountService;

    @Operation(summary = "내 네이버 계정 연결 상태", description = "비밀키는 돌려주지 않는다.")
    @GetMapping
    public ResponseEntity<ApiResponse<AccountStatus>> status() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.status(CurrentUser.id())));
    }

    @Operation(summary = "내 네이버 계정 연결·교체",
            description = "실계정 모드에서는 입력한 키로 조회가 되는지 확인한 뒤에만 저장한다.")
    @PutMapping
    public ResponseEntity<ApiResponse<AccountStatus>> connect(@Valid @RequestBody ConnectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.connect(CurrentUser.id(), request)));
    }

    @Operation(summary = "내 네이버 계정 연결 끊기", description = "저장된 키만 지운다. 쌓인 광고 데이터는 남는다.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<AccountStatus>> disconnect() {
        return ResponseEntity.ok(ApiResponse.ok(accountService.disconnect(CurrentUser.id())));
    }
}
