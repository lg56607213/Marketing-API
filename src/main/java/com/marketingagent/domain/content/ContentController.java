package com.marketingagent.domain.content;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.content.dto.ApproveRequest;
import com.marketingagent.domain.content.dto.ContentResponse;
import com.marketingagent.domain.content.dto.GenerateRequest;
import com.marketingagent.domain.content.dto.RewriteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
@Tag(name = "Content", description = "AI 마케팅 콘텐츠 생성 및 관리")
@SecurityRequirement(name = "bearerAuth")
public class ContentController {

    private final ContentService contentService;

    @Operation(summary = "콘텐츠 AI 생성")
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ContentResponse>> generate(@Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(contentService.generate(request)));
    }

    @Operation(summary = "콘텐츠 재작성")
    @PostMapping("/{id}/rewrite")
    public ResponseEntity<ApiResponse<ContentResponse>> rewrite(
            @PathVariable Long id, @Valid @RequestBody RewriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(contentService.rewrite(id, request)));
    }

    @Operation(summary = "콘텐츠 승인")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ContentResponse>> approve(
            @PathVariable Long id, @RequestBody ApproveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.approve(id, request)));
    }

    @Operation(summary = "콘텐츠 반려")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ContentResponse>> reject(
            @PathVariable Long id, @RequestBody ApproveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.reject(id, request)));
    }

    @Operation(summary = "콘텐츠 게시")
    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ContentResponse>> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.publish(id)));
    }

    @Operation(summary = "콘텐츠 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ContentResponse>>> findAll(
            @RequestParam(required = false) Long brandId) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.findAll(brandId)));
    }

    @Operation(summary = "콘텐츠 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContentResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.findById(id)));
    }
}
