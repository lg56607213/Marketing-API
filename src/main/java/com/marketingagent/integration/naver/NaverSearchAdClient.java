package com.marketingagent.integration.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.StatRow;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 네이버 검색광고 API 실제 클라이언트.
 * 모든 요청에 X-Timestamp / X-API-KEY / X-Customer / X-Signature 헤더를 붙인다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naver.searchad.provider", havingValue = "naver")
public class NaverSearchAdClient implements SearchAdClient {

    /** 한 번의 /stats 호출에 넣는 최대 ID 개수. 너무 많으면 URL 길이 제한에 걸린다. */
    private static final int STAT_BATCH_SIZE = 100;

    private static final List<String> STAT_FIELDS =
            List.of("impCnt", "clkCnt", "salesAmt", "ctr", "cpc", "avgRnk", "ccnt");

    private final NaverSearchAdProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public NaverSearchAdClient(NaverSearchAdProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        if (isBlank(properties.customerId()) || isBlank(properties.apiKey()) || isBlank(properties.secretKey())) {
            log.warn("네이버 검색광고 자격증명이 비어 있습니다. NAVER_CUSTOMER_ID / NAVER_API_KEY / NAVER_SECRET_KEY 를 설정하세요.");
        }
    }

    @Override
    public List<NccCampaign> listCampaigns() {
        return get("/ncc/campaigns", "", new ParameterizedTypeReference<List<NccCampaign>>() {});
    }

    @Override
    public List<NccAdgroup> listAdgroups(String nccCampaignId) {
        return get("/ncc/adgroups", "nccCampaignId=" + encode(nccCampaignId),
                new ParameterizedTypeReference<List<NccAdgroup>>() {});
    }

    @Override
    public List<NccKeyword> listKeywords(String nccAdgroupId) {
        return get("/ncc/keywords", "nccAdgroupId=" + encode(nccAdgroupId),
                new ParameterizedTypeReference<List<NccKeyword>>() {});
    }

    @Override
    public List<StatRow> dailyStats(List<String> ids, LocalDate since, LocalDate until) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<StatRow> rows = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += STAT_BATCH_SIZE) {
            List<String> batch = ids.subList(from, Math.min(from + STAT_BATCH_SIZE, ids.size()));
            rows.addAll(fetchStatBatch(batch, since, until));
        }
        return rows;
    }

    private List<StatRow> fetchStatBatch(List<String> ids, LocalDate since, LocalDate until) {
        String query = "ids=" + encode(toJson(ids))
                + "&fields=" + encode(toJson(STAT_FIELDS))
                + "&timeRange=" + encode("{\"since\":\"" + since + "\",\"until\":\"" + until + "\"}")
                + "&timeIncrement=1";

        JsonNode response = get("/stats", query, new ParameterizedTypeReference<JsonNode>() {});
        JsonNode data = response.path("data");
        if (!data.isArray()) {
            log.warn("/stats 응답에 data 배열이 없습니다. 응답 형태를 확인하세요: {}", response);
            return List.of();
        }

        List<StatRow> rows = new ArrayList<>();
        for (JsonNode node : data) {
            LocalDate statDate = readDate(node);
            if (statDate == null) {
                log.warn("/stats 응답에서 일자 필드를 찾지 못했습니다: {}", node);
                continue;
            }
            rows.add(new StatRow(
                    node.path("id").asText(),
                    statDate,
                    node.path("impCnt").asLong(),
                    node.path("clkCnt").asLong(),
                    node.path("salesAmt").asLong(),
                    node.path("ctr").asDouble(),
                    node.path("cpc").asDouble(),
                    node.path("avgRnk").asDouble(),
                    node.path("ccnt").asLong()));
        }
        return rows;
    }

    /** 일자 필드명이 응답 종류에 따라 다르게 내려와서 알려진 후보를 모두 확인한다. */
    private LocalDate readDate(JsonNode node) {
        for (String field : List.of("statDt", "dateTime", "statDate", "date", "day")) {
            String raw = node.path(field).asText(null);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
            } catch (DateTimeParseException ignored) {
                // 다음 후보 확인
            }
        }
        return null;
    }

    @Override
    public long updateKeywordBid(String nccKeywordId, String nccAdgroupId, String keyword, long bidAmt) {
        String path = "/ncc/keywords/" + nccKeywordId;
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(properties.secretKey(), timestamp, "PUT", path);
        URI uri = URI.create(properties.baseUrl() + path + "?fields=bidAmt");

        Map<String, Object> body = Map.of(
                "nccKeywordId", nccKeywordId,
                "nccAdgroupId", nccAdgroupId,
                "keyword", keyword,
                "bidAmt", bidAmt,
                "useGroupBidAmt", false);

        try {
            NccKeyword updated = restClient.put()
                    .uri(uri)
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", properties.apiKey())
                    .header("X-Customer", properties.customerId())
                    .header("X-Signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(NccKeyword.class);

            if (updated == null || updated.bidAmt() == null) {
                log.warn("입찰가 변경 응답이 비어 있습니다. 요청값을 반영값으로 간주합니다: {}", nccKeywordId);
                return bidAmt;
            }
            return updated.bidAmt();
        } catch (RestClientResponseException e) {
            log.error("입찰가 변경 실패 {} -> {} {}", nccKeywordId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("네이버 입찰가 변경에 실패했습니다: " + e.getStatusCode());
        }
    }

    private <T> T get(String path, String query, ParameterizedTypeReference<T> type) {
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(properties.secretKey(), timestamp, "GET", path);
        URI uri = URI.create(properties.baseUrl() + path + (query.isEmpty() ? "" : "?" + query));

        try {
            T body = restClient.get()
                    .uri(uri)
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", properties.apiKey())
                    .header("X-Customer", properties.customerId())
                    .header("X-Signature", signature)
                    .retrieve()
                    .body(type);
            return body != null ? body : emptyFor(type);
        } catch (RestClientResponseException e) {
            log.error("네이버 검색광고 API 호출 실패 {} {} -> {} {}", "GET", path,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("네이버 검색광고 API 호출에 실패했습니다: " + e.getStatusCode());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T emptyFor(ParameterizedTypeReference<T> type) {
        return (T) Collections.emptyList();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("쿼리 파라미터 직렬화에 실패했습니다", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
