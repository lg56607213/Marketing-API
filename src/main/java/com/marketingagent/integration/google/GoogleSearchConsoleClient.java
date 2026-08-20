package com.marketingagent.integration.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketingagent.common.exception.BadRequestException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 구글 서치콘솔 Search Analytics API 클라이언트.
 * 리프레시 토큰으로 액세스 토큰을 그때그때 발급받아 호출한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "google.searchconsole.provider", havingValue = "google")
public class GoogleSearchConsoleClient implements SearchConsoleClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String API_BASE = "https://searchconsole.googleapis.com/webmasters/v3/sites/";

    private final GoogleSearchConsoleProperties properties;
    private final RestClient restClient = RestClient.create();

    public GoogleSearchConsoleClient(GoogleSearchConsoleProperties properties) {
        this.properties = properties;
        if (!properties.configured()) {
            log.warn("구글 서치콘솔 설정이 비어 있습니다. GSC_SITE_URL / GSC_CLIENT_ID / GSC_CLIENT_SECRET / GSC_REFRESH_TOKEN 을 확인하세요.");
        }
    }

    @Override
    public List<SearchConsoleRow> query(LocalDate since, LocalDate until, String dimension, int limit) {
        if (!properties.configured()) {
            throw new BadRequestException("구글 서치콘솔 설정이 완료되지 않았습니다.");
        }

        String token = accessToken();
        String url = API_BASE + encode(properties.siteUrl()) + "/searchAnalytics/query";
        Map<String, Object> body = Map.of(
                "startDate", since.toString(),
                "endDate", until.toString(),
                "dimensions", List.of(dimension),
                "rowLimit", limit);

        try {
            JsonNode response = restClient.post()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            List<SearchConsoleRow> rows = new ArrayList<>();
            if (response == null) {
                return rows;
            }
            for (JsonNode node : response.path("rows")) {
                rows.add(new SearchConsoleRow(
                        node.path("keys").path(0).asText(),
                        node.path("impressions").asLong(),
                        node.path("clicks").asLong(),
                        round2(node.path("ctr").asDouble() * 100),
                        round2(node.path("position").asDouble())));
            }
            return rows;
        } catch (RestClientResponseException e) {
            log.error("서치콘솔 조회 실패 {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("구글 서치콘솔 조회에 실패했습니다: " + e.getStatusCode());
        }
    }

    @Override
    public Health health() {
        if (!properties.configured()) {
            return new Health("google", false, false,
                    "설정이 비어 있습니다. 사이트 주소와 OAuth 자격증명 3종을 채우세요.");
        }
        try {
            int rows = query(LocalDate.now().minusDays(7), LocalDate.now().minusDays(1), "query", 1).size();
            return new Health("google", true, true, "연결 정상입니다. 최근 7일 데이터 " + rows + "행을 확인했습니다.");
        } catch (Exception e) {
            return new Health("google", true, false, "호출에 실패했습니다: " + e.getMessage());
        }
    }

    /** 리프레시 토큰으로 액세스 토큰을 발급받는다. 유효기간이 짧아 매번 새로 받는다. */
    private String accessToken() {
        String form = "client_id=" + encode(properties.clientId())
                + "&client_secret=" + encode(properties.clientSecret())
                + "&refresh_token=" + encode(properties.refreshToken())
                + "&grant_type=refresh_token";
        try {
            JsonNode response = restClient.post()
                    .uri(URI.create(TOKEN_URL))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String token = response != null ? response.path("access_token").asText(null) : null;
            if (token == null) {
                throw new BadRequestException("액세스 토큰을 받지 못했습니다. 리프레시 토큰을 확인하세요.");
            }
            return token;
        } catch (RestClientResponseException e) {
            log.error("구글 토큰 발급 실패 {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("구글 인증에 실패했습니다: " + e.getStatusCode());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
