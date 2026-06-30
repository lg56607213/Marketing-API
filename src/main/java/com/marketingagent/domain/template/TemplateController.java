package com.marketingagent.domain.template;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.common.ContentType;
import com.marketingagent.domain.template.dto.TemplateRequest;
import com.marketingagent.domain.template.dto.TemplateResponse;
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
@RequestMapping("/api/template")
@RequiredArgsConstructor
@Tag(name = "Template", description = "콘텐츠 템플릿 관리")
@SecurityRequirement(name = "bearerAuth")
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "템플릿 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<TemplateResponse>> create(@Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(templateService.create(request)));
    }

    @Operation(summary = "템플릿 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> findAll(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) ContentType contentType) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.findAll(brandId, contentType)));
    }

    @Operation(summary = "템플릿 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.findById(id)));
    }

    @Operation(summary = "템플릿 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> update(@PathVariable Long id,
            @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.update(id, request)));
    }

    @Operation(summary = "템플릿 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
