package com.marketingagent.domain.subsidy;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.domain.subsidy.SubsidyDtos.JobRunView;
import com.marketingagent.domain.subsidy.SubsidyDtos.MatchView;
import com.marketingagent.domain.subsidy.SubsidyDtos.ProfileRequest;
import com.marketingagent.domain.subsidy.SubsidyDtos.ProfileView;
import com.marketingagent.domain.subsidy.SubsidyDtos.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Subsidy", description = "정부지원사업 자동 매칭 (정보 제공용, 공고 원문 확인 필수)")
public class SubsidyController {

    private final SubsidyProfileService profileService;
    private final SubsidyCatalogService catalogService;
    private final SubsidyService subsidyService;
    private final SubsidyProperties properties;
    private final SubsidyAi ai;
    private final SubsidyAnnouncementRepository announcementRepository;
    private final SubsidyProfileRepository profileRepository;
    private final SubsidyJobRunRepository jobRunRepository;

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/subsidy/profiles")
    public ResponseEntity<ApiResponse<List<ProfileView>>> profiles() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.list()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/subsidy/profiles")
    public ResponseEntity<ApiResponse<ProfileView>> create(@RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.create(request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/subsidy/profiles/{id}")
    public ResponseEntity<ApiResponse<ProfileView>> update(@PathVariable Long id, @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.update(id, request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/subsidy/profiles/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        profileService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "사업체 다시 판정", description = "이미 모은 공고로 지금 다시 판정한다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/subsidy/profiles/{id}/rematch")
    public ResponseEntity<ApiResponse<Integer>> rematch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.rematch(id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/subsidy/profiles/{id}/matches")
    public ResponseEntity<ApiResponse<List<MatchView>>> matches(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.matches(id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/subsidy/matches/{id}/star")
    public ResponseEntity<ApiResponse<Void>> star(@PathVariable Long id, @RequestParam boolean value) {
        profileService.star(id, value);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- 지원사업 직접 등록 · 검색 ---

    @Operation(summary = "직접 등록한 지원사업 목록 (내 업체 + 공용)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/subsidy/manual")
    public ResponseEntity<ApiResponse<List<SubsidyCatalogService.ManualView>>> manual() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listManual()));
    }

    @Operation(summary = "지원사업 직접 등록", description = "수집 대상이 아닌 사업을 넣는다. 넣는 즉시 자격요건을 분석하고 내 사업자를 다시 판정한다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/subsidy/manual")
    public ResponseEntity<ApiResponse<SubsidyCatalogService.ManualView>> createManual(
            @RequestBody SubsidyCatalogService.ManualRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.createManual(request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/subsidy/manual/{id}")
    public ResponseEntity<ApiResponse<SubsidyCatalogService.ManualView>> updateManual(@PathVariable Long id,
            @RequestBody SubsidyCatalogService.ManualRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.updateManual(id, request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/subsidy/manual/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteManual(@PathVariable Long id) {
        catalogService.deleteManual(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "지원사업 검색", description = "q=업무 말(공백·쉼표 여러 개), profileId=사업자 조건으로 판정. 둘 중 하나는 필요")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/subsidy/search")
    public ResponseEntity<ApiResponse<List<SubsidyCatalogService.SearchItem>>> search(
            @RequestParam(required = false) String q, @RequestParam(required = false) Long profileId,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            @RequestParam(defaultValue = "false") boolean hideNo) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.search(q, profileId, includeClosed, hideNo)));
    }

    // --- 관리자 ---

    @Operation(summary = "수집·분석·매칭·알림 수동 실행 (관리자)", description = "job=collect|analyze|match|notify|all")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/api/subsidy/admin/run")
    public ResponseEntity<ApiResponse<List<String>>> run(@RequestParam(defaultValue = "all") String job) {
        List<String> out = new java.util.ArrayList<>();
        if (job.equals("collect") || job.equals("all")) {
            out.add(subsidyService.collect());
        }
        if (job.equals("analyze") || job.equals("all")) {
            out.add(subsidyService.analyze());
        }
        if (job.equals("match") || job.equals("all")) {
            out.add(subsidyService.matchAll());
        }
        if (job.equals("notify")) {
            out.add(subsidyService.sendNotifications());
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/api/subsidy/admin/status")
    public ResponseEntity<ApiResponse<Status>> status() {
        List<JobRunView> runs = jobRunRepository.findTop30ByOrderByIdDesc().stream()
                .map(r -> new JobRunView(r.getId(), r.getJob(), r.getStartedAt(), r.getFinishedAt(), r.getStatus(), r.getDetail()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new Status(properties.enabled(), properties.hasBizinfo(),
                properties.hasKstartup(), ai.available(), announcementRepository.count(),
                announcementRepository.countByAnalyzedAtIsNull(), profileRepository.count(), runs)));
    }

    // --- 공개: 메일 수신거부 (로그인 없이 서명 확인) ---

    @GetMapping(value = "/api/public/subsidy/unsubscribe", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> unsubscribe(@RequestParam("p") Long profileId, @RequestParam("t") String token) {
        boolean ok = subsidyService.unsubscribe(profileId, token);
        String msg = ok ? "이 사업체의 지원사업 알림 메일을 더 이상 보내지 않습니다. 화면에서 다시 켤 수 있습니다."
                : "링크가 올바르지 않습니다. 메일의 링크를 다시 눌러 주세요.";
        return ResponseEntity.ok("<!doctype html><meta charset=\"utf-8\"><title>수신거부</title>"
                + "<p style=\"font-family:sans-serif;padding:24px\">" + msg + "</p>");
    }
}
