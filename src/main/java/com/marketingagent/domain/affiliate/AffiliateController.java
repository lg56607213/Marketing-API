package com.marketingagent.domain.affiliate;

import com.marketingagent.common.ApiResponse;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.affiliate.AffiliateDtos.PostRequest;
import com.marketingagent.domain.affiliate.AffiliateDtos.PostView;
import com.marketingagent.domain.affiliate.AffiliateDtos.SettingsRequest;
import com.marketingagent.domain.affiliate.AffiliateDtos.SettingsView;
import com.marketingagent.domain.affiliate.AffiliateDtos.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Affiliate", description = "브랜드커넥트 상품 블로그 글 초안 (발행은 사용자가 복사·붙여넣기)")
public class AffiliateController {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final AffiliateService service;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @GetMapping("/api/affiliate/status")
    public ResponseEntity<ApiResponse<Status>> status() {
        return ResponseEntity.ok(ApiResponse.ok(service.status()));
    }

    @GetMapping("/api/affiliate/settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings() {
        return ResponseEntity.ok(ApiResponse.ok(service.settings()));
    }

    @Operation(summary = "네이버 검색 API 키 저장", description = "네이버에 실제로 검색해 키가 맞는지 확인한 뒤 암호화해 저장한다.")
    @PutMapping("/api/affiliate/settings")
    public ResponseEntity<ApiResponse<SettingsView>> saveSettings(@RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveSettings(request)));
    }

    @DeleteMapping("/api/affiliate/settings")
    public ResponseEntity<ApiResponse<Void>> deleteSettings() {
        service.deleteSettings();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/api/affiliate/posts")
    public ResponseEntity<ApiResponse<List<PostView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @GetMapping("/api/affiliate/posts/{id}")
    public ResponseEntity<ApiResponse<PostView>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @Operation(summary = "상품 1개로 블로그 글 초안 만들기", description = "네이버 쇼핑 검색으로 상품 정보를 확인한 뒤 AI 가 쓴다. 30초 안팎 걸린다.")
    @PostMapping("/api/affiliate/posts")
    public ResponseEntity<ApiResponse<PostView>> create(@RequestBody PostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(request)));
    }

    @PostMapping("/api/affiliate/posts/{id}/rewrite")
    public ResponseEntity<ApiResponse<PostView>> rewrite(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.rewrite(id)));
    }

    @DeleteMapping("/api/affiliate/posts/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** 상품 대표 사진 내려받기. 네이버 쇼핑 사진 서버(pstatic.net)만 허용한다. */
    @GetMapping("/api/affiliate/image")
    public ResponseEntity<byte[]> image(@RequestParam String url) throws Exception {
        if (!allowedImage(url)) {
            throw new BadRequestException("네이버 쇼핑 사진만 내려받을 수 있습니다.");
        }
        HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        String type = res.headers().firstValue("content-type").orElse("");
        if (res.statusCode() != 200 || !type.startsWith("image/") || res.body().length > MAX_IMAGE_BYTES) {
            throw new BadRequestException("사진을 받지 못했습니다.");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(res.body());
    }

    static boolean allowedImage(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            return "https".equals(u.getScheme()) && host != null && u.getPort() == -1
                    && (host.endsWith(".pstatic.net"));
        } catch (Exception e) {
            return false;
        }
    }
}
