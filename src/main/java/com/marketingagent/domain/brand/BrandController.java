package com.marketingagent.domain.brand;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.brand.dto.BrandRequest;
import com.marketingagent.domain.brand.dto.BrandResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brand")
@RequiredArgsConstructor
@Tag(name = "Brand", description = "브랜드 관리")
@SecurityRequirement(name = "bearerAuth")
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "브랜드 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> create(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(brandService.create(request)));
    }

    @Operation(summary = "브랜드 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(brandService.findAll()));
    }

    @Operation(summary = "브랜드 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(brandService.findById(id)));
    }

    @Operation(summary = "브랜드 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> update(@PathVariable Long id,
            @Valid @RequestBody BrandRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(brandService.update(id, request)));
    }

    @Operation(summary = "브랜드 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
