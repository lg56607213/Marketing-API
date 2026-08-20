package com.marketingagent.domain.ads;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.ads.dto.KeywordIdeaResponse;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.integration.naver.dto.NccKeyword;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads/keywords")
@RequiredArgsConstructor
@Tag(name = "KeywordTool", description = "키워드 발굴 및 등록/제외 관리")
@SecurityRequirement(name = "bearerAuth")
public class KeywordController {

    private final KeywordManagementService keywordManagementService;

    @Operation(summary = "연관 키워드 발굴",
            description = "키워드도구로 연관 키워드와 월간 검색량을 조회한다. 계정을 변경하지 않는다.")
    @GetMapping("/ideas")
    public ResponseEntity<ApiResponse<List<KeywordIdeaResponse>>> ideas(
            @RequestParam String hints,
            @RequestParam(defaultValue = "false") boolean excludeRegistered,
            @RequestParam(defaultValue = "true") boolean mustContainHint) {
        List<String> parsed = Arrays.stream(hints.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(
                keywordManagementService.findIdeas(parsed, excludeRegistered, mustContainHint)));
    }

    @Operation(summary = "키워드 등록", description = "선택한 키워드를 광고그룹에 추가한다. 네이버 계정이 실제로 변경된다.")
    @PostMapping
    public ResponseEntity<ApiResponse<List<NccKeyword>>> add(
            @RequestParam String nccAdgroupId,
            @RequestParam(required = false) Integer position,
            @RequestParam(required = false) String device,
            @RequestBody List<String> keywords) {
        return ResponseEntity.ok(ApiResponse.ok(
                keywordManagementService.addKeywords(nccAdgroupId, keywords, position, device)));
    }

    @Operation(summary = "키워드 삭제")
    @DeleteMapping("/{nccKeywordId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable String nccKeywordId) {
        keywordManagementService.removeKeyword(nccKeywordId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "제외 키워드 조회")
    @GetMapping("/restricted")
    public ResponseEntity<ApiResponse<List<SearchAdClient.RestrictedKeyword>>> restricted(
            @RequestParam String nccAdgroupId) {
        return ResponseEntity.ok(ApiResponse.ok(keywordManagementService.listRestricted(nccAdgroupId)));
    }

    @Operation(summary = "제외 키워드 추가", description = "기본 유형은 확장검색 제외(EXP_SEARCH)이다.")
    @PostMapping("/restricted")
    public ResponseEntity<ApiResponse<List<SearchAdClient.RestrictedKeyword>>> addRestricted(
            @RequestParam String nccAdgroupId,
            @RequestParam(required = false) String type,
            @RequestBody List<String> keywords) {
        return ResponseEntity.ok(ApiResponse.ok(
                keywordManagementService.addRestricted(nccAdgroupId, keywords, type)));
    }

    @Operation(summary = "제외 키워드 삭제")
    @DeleteMapping("/restricted/{restrictedKeywordId}")
    public ResponseEntity<ApiResponse<Void>> removeRestricted(
            @RequestParam String nccAdgroupId,
            @PathVariable String restrictedKeywordId) {
        keywordManagementService.removeRestricted(nccAdgroupId, restrictedKeywordId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
