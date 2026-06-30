package com.marketingagent.domain.prompt;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.prompt.dto.PromptRequest;
import com.marketingagent.domain.prompt.dto.PromptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompt")
@RequiredArgsConstructor
@Tag(name = "Prompt", description = "AI 프롬프트 템플릿 관리")
@SecurityRequirement(name = "bearerAuth")
public class PromptController {

    private final PromptService promptService;

    @Operation(summary = "프롬프트 템플릿 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<PromptResponse>> create(@Valid @RequestBody PromptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(promptService.create(request)));
    }

    @Operation(summary = "프롬프트 템플릿 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptResponse>>> findAll(
            @RequestParam(required = false) Long brandId) {
        return ResponseEntity.ok(ApiResponse.ok(promptService.findAll(brandId)));
    }

    @Operation(summary = "프롬프트 템플릿 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(promptService.findById(id)));
    }

    @Operation(summary = "프롬프트 템플릿 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptResponse>> update(@PathVariable Long id,
            @Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(promptService.update(id, request)));
    }

    @Operation(summary = "프롬프트 템플릿 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        promptService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
