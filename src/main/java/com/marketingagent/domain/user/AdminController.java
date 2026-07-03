package com.marketingagent.domain.user;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.user.dto.RegisterRequest;
import com.marketingagent.domain.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "관리자 전용 API")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final UserService userService;

    @Operation(summary = "전체 사용자 목록")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserSummary>>> listUsers() {
        List<UserSummary> users = userRepository.findAll()
                .stream().map(UserSummary::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @Operation(summary = "사용자 계정 생성 (관리자 전용)")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserSummary>> createUser(@Valid @RequestBody RegisterRequest request) {
        var auth = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new UserSummary(null, auth.email(), auth.role(), null)));
    }
}
