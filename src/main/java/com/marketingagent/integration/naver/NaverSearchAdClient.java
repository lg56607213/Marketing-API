package com.marketingagent.integration.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.integration.naver.dto.ConversionRow;
import com.marketingagent.integration.naver.dto.HourlyStatRow;
import com.marketingagent.integration.naver.dto.NccAdgroup;
import com.marketingagent.integration.naver.dto.NccCampaign;
import com.marketingagent.integration.naver.dto.NccKeyword;
import com.marketingagent.integration.naver.dto.PerformanceEstimate;
import com.marketingagent.integration.naver.dto.RelatedKeyword;
import com.marketingagent.integration.naver.dto.ReportProbe;
import com.marketingagent.integration.naver.dto.SearchQueryRow;
import com.marketingagent.integration.naver.dto.StatRow;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 네이버 검색광고 API 실제 클라이언트.
 * 모든 요청에 X-Timestamp / X-API-KEY / X-Customer / X-Signature 헤더를 붙인다.
 * 자격증명은 요청마다 지금 일하고 있는 회원의 것을 받아 쓴다.
 */
@Slf4j
public class NaverSearchAdClient implements SearchAdClient {

    /**
     * /stats 는 일자별 조회(timeIncrement=1)를 단건 id 로만 지원한다.
     * ids 목록은 timeIncrement=allDays 요약에서만 받으므로 일자별은 키워드마다 한 번씩 호출한다.
     */

    private static final int REPORT_MAX_ATTEMPTS = 15;
    private static final long REPORT_POLL_INTERVAL_MS = 2500L;

    private static final List<String> STAT_FIELDS =
            List.of("impCnt", "clkCnt", "salesAmt", "ctr", "cpc", "avgRnk", "ccnt");

    private final NaverCredentials properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public NaverSearchAdClient(NaverCredentials properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        if (isBlank(properties.customerId()) || isBlank(properties.apiKey()) || isBlank(properties.secretKey())) {
            throw new BadRequestException("네이버 검색광고 자격증명이 비어 있습니다. 광고 계정 설정에서 고객 ID · 액세스 라이선스 · 비밀키를 입력하세요.");
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
        List<StatRow> rows = new ArrayList<>();
        for (String id : ids) {
            rows.addAll(fetchDailyStats(id, since, until));
        }
        return rows;
    }

    private List<StatRow> fetchDailyStats(String id, LocalDate since, LocalDate until) {
        String query = "id=" + encode(id)
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
            // 일자별 응답에는 id 가 실려 오지 않으므로 요청한 id 를 그대로 붙인다.
            rows.add(new StatRow(
                    id,
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
        for (String field : List.of("dateStart", "statDt", "dateTime", "statDate", "date", "day")) {
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
        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "PUT", path);
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
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
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

    private NaverCredentials credentials() {
        return properties;
    }

    @Override
    public Map<String, Long> estimateBidForPosition(List<String> keywords, int position, String device) {
        return doEstimateBidForPosition(keywords, position, device);
    }

    private Map<String, Long> doEstimateBidForPosition(List<String> keywords, int position, String device) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (keywords.isEmpty()) {
            return result;
        }

        String path = "/estimate/average-position-bid/keyword";
        List<Map<String, Object>> items = keywords.stream()
                .map(keyword -> Map.<String, Object>of("key", keyword, "position", position))
                .toList();
        Map<String, Object> body = Map.of("device", device, "items", items, "keywordplus", false);

        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "POST", path);

        try {
            JsonNode response = restClient.post()
                    .uri(URI.create(properties.baseUrl() + path))
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
                    .header("X-Signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return result;
            }
            for (JsonNode node : response.path("estimate")) {
                result.put(node.path("keyword").asText(), node.path("bid").asLong());
            }
            return result;
        } catch (RestClientResponseException e) {
            log.error("입찰가 추정 실패 {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("네이버 입찰가 추정에 실패했습니다: " + e.getStatusCode());
        }
    }

    @Override
    public List<RelatedKeyword> relatedKeywords(List<String> hints) {
        return doRelatedKeywords(hints);
    }

    private List<RelatedKeyword> doRelatedKeywords(List<String> hints) {
        if (hints.isEmpty()) {
            return List.of();
        }
        // 키워드도구는 공백을 제거한 힌트를 최대 5개까지 받는다.
        String joined = hints.stream().limit(5).map(h -> h.replace(" ", "")).collect(Collectors.joining(","));
        JsonNode response = get("/keywordstool", "hintKeywords=" + encode(joined) + "&showDetail=1",
                new ParameterizedTypeReference<JsonNode>() {});

        List<RelatedKeyword> result = new ArrayList<>();
        for (JsonNode node : response.path("keywordList")) {
            result.add(new RelatedKeyword(
                    node.path("relKeyword").asText(),
                    parseCount(node.path("monthlyPcQcCnt")),
                    parseCount(node.path("monthlyMobileQcCnt")),
                    node.path("monthlyAvePcClkCnt").asDouble(),
                    node.path("monthlyAveMobileClkCnt").asDouble(),
                    node.path("compIdx").asText("-")));
        }
        return result;
    }

    /** 검색량이 적으면 숫자 대신 "< 10" 같은 문자열이 온다. */
    private long parseCount(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }
        String raw = node.asText("").replaceAll("[^0-9]", "");
        return raw.isEmpty() ? 0 : Long.parseLong(raw);
    }

    @Override
    public List<SearchQueryRow> searchQueryReport(LocalDate date) {
        return StatReportParser.searchQueries(date, downloadReport(StatReportParser.EXPKEYWORD, date));
    }

    @Override
    public List<HourlyStatRow> hourlyStatReport(LocalDate date) {
        return StatReportParser.hourlyStats(date, downloadReport(StatReportParser.AD_DETAIL, date));
    }

    @Override
    public List<ConversionRow> conversionReport(LocalDate date) {
        return StatReportParser.conversions(date, downloadReport(StatReportParser.AD_CONVERSION_DETAIL, date));
    }

    @Override
    public ReportProbe probeReport(String reportTp, LocalDate date) {
        List<String[]> lines = StatReportParser.split(downloadReport(reportTp, date));
        return new ReportProbe(lines.size(), lines.isEmpty() ? 0 : lines.get(0).length);
    }

    /**
     * 대용량 보고서를 만들고 완성되면 본문을 내려받는다.
     *
     * @return 탭 구분 본문. 그 날짜에 데이터가 없으면 빈 문자열
     * @throws IllegalStateException 리포트 생성이 실패했거나 제한 시간 안에 끝나지 않은 경우
     */
    private String downloadReport(String reportTp, LocalDate date) {
        String statDt = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        JsonNode job = post("/stat-reports", Map.of("reportTp", reportTp, "statDt", statDt));
        long jobId = job.path("reportJobId").asLong();
        if (jobId == 0) {
            throw new IllegalStateException(reportTp + " 리포트 생성 요청이 거부됐습니다: " + job);
        }
        String downloadUrl = awaitReport(reportTp, jobId);
        return downloadUrl == null ? "" : getRaw(downloadUrl);
    }

    /**
     * 리포트는 비동기로 만들어진다. BUILT 가 될 때까지 짧게 기다린다.
     *
     * @return 다운로드 URL. 데이터가 없다고 응답한 날(NONE)이면 null
     */
    private String awaitReport(String reportTp, long jobId) {
        for (int attempt = 0; attempt < REPORT_MAX_ATTEMPTS; attempt++) {
            JsonNode info = get("/stat-reports/" + jobId, "", new ParameterizedTypeReference<JsonNode>() {});
            String status = info.path("status").asText();
            if ("BUILT".equals(status)) {
                String url = info.path("downloadUrl").asText(null);
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException(reportTp + " 리포트에 다운로드 주소가 없습니다: " + info);
                }
                return url;
            }
            if ("NONE".equals(status)) {
                log.info("{} 리포트에 데이터가 없습니다: jobId={}", reportTp, jobId);
                return null;
            }
            if ("ERROR".equals(status)) {
                throw new IllegalStateException(reportTp + " 리포트 생성이 실패했습니다: " + info);
            }
            try {
                Thread.sleep(REPORT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(reportTp + " 리포트 대기가 중단됐습니다.");
            }
        }
        throw new IllegalStateException(reportTp + " 리포트가 제한 시간 안에 완성되지 않았습니다: jobId=" + jobId);
    }

    @Override
    public List<NccKeyword> createKeywords(String nccAdgroupId, List<NewKeyword> keywords) {
        List<Map<String, Object>> body = keywords.stream()
                .map(k -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("keyword", k.keyword());
                    item.put("useGroupBidAmt", k.bidAmt() == null);
                    if (k.bidAmt() != null) {
                        item.put("bidAmt", k.bidAmt());
                    }
                    item.put("userLock", false);
                    return item;
                })
                .toList();

        JsonNode response = post("/ncc/keywords?nccAdgroupId=" + encode(nccAdgroupId), body);
        List<NccKeyword> created = new ArrayList<>();
        for (JsonNode node : response) {
            created.add(new NccKeyword(
                    node.path("nccKeywordId").asText(), node.path("nccAdgroupId").asText(),
                    node.path("keyword").asText(), node.path("status").asText(),
                    node.path("bidAmt").asLong(), node.path("useGroupBidAmt").asBoolean(),
                    node.path("userLock").asBoolean()));
        }
        return created;
    }

    @Override
    public void deleteKeyword(String nccKeywordId) {
        delete("/ncc/keywords/" + nccKeywordId, "");
    }

    @Override
    public List<RestrictedKeyword> listRestrictedKeywords(String nccAdgroupId) {
        return toRestricted(get("/ncc/adgroups/" + nccAdgroupId + "/restricted-keywords", "",
                new ParameterizedTypeReference<JsonNode>() {}));
    }

    @Override
    public List<RestrictedKeyword> addRestrictedKeywords(String nccAdgroupId, List<String> keywords, String type) {
        List<Map<String, Object>> body = keywords.stream()
                .map(k -> Map.<String, Object>of("keyword", k, "type", type))
                .toList();
        return toRestricted(post("/ncc/adgroups/" + nccAdgroupId + "/restricted-keywords", body));
    }

    @Override
    public void deleteRestrictedKeyword(String nccAdgroupId, String restrictedKeywordId) {
        delete("/ncc/adgroups/" + nccAdgroupId + "/restricted-keywords", "ids=" + encode(restrictedKeywordId));
    }

    private List<RestrictedKeyword> toRestricted(JsonNode response) {
        List<RestrictedKeyword> result = new ArrayList<>();
        for (JsonNode node : response) {
            result.add(new RestrictedKeyword(
                    node.path("nccAdgroupRestrictKwdId").asText(),
                    node.path("keyword").asText(),
                    node.path("type").asText()));
        }
        return result;
    }

    private JsonNode post(String pathWithQuery, Object body) {
        String path = pathWithQuery.split("\\?")[0];
        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "POST", path);
        try {
            JsonNode response = restClient.post()
                    .uri(URI.create(properties.baseUrl() + pathWithQuery))
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
                    .header("X-Signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return response != null ? response : objectMapper.createObjectNode();
        } catch (RestClientResponseException e) {
            log.error("네이버 API POST 실패 {} -> {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("네이버 API 호출에 실패했습니다: " + e.getResponseBodyAsString());
        }
    }

    /** 성과 예측은 한 번에 200건까지 받는다. */
    private static final int ESTIMATE_CHUNK = 200;

    @Override
    public List<PerformanceEstimate> estimatePerformance(List<PerformanceEstimate.Query> queries, String device) {
        return doEstimatePerformance(queries, device);
    }

    private List<PerformanceEstimate> doEstimatePerformance(List<PerformanceEstimate.Query> queries, String device) {
        List<PerformanceEstimate> result = new ArrayList<>();
        for (int from = 0; from < queries.size(); from += ESTIMATE_CHUNK) {
            List<Map<String, Object>> items = queries.subList(from, Math.min(from + ESTIMATE_CHUNK, queries.size()))
                    .stream()
                    .map(q -> Map.<String, Object>of("device", device, "keyword", q.keyword(), "bid", q.bid()))
                    .toList();
            JsonNode response = post("/estimate/performance-bulk", Map.of("items", items));
            for (JsonNode node : response.path("items")) {
                result.add(new PerformanceEstimate(node.path("keyword").asText(), node.path("bid").asLong(),
                        node.path("impressions").asLong(), node.path("clicks").asLong(),
                        node.path("cost").asLong()));
            }
        }
        return result;
    }

    @Override
    public long updateCampaignDailyBudget(String nccCampaignId, long dailyBudget) {
        JsonNode updated = put("/ncc/campaigns/" + nccCampaignId, "fields=budget", Map.of(
                "nccCampaignId", nccCampaignId,
                "dailyBudget", dailyBudget,
                "useDailyBudget", true));
        return updated.path("dailyBudget").asLong(dailyBudget);
    }

    @Override
    public void setKeywordPaused(String nccKeywordId, String nccAdgroupId, boolean paused) {
        put("/ncc/keywords/" + nccKeywordId, "fields=userLock", Map.of(
                "nccKeywordId", nccKeywordId,
                "nccAdgroupId", nccAdgroupId,
                "userLock", paused));
    }

    private JsonNode put(String path, String query, Object body) {
        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "PUT", path);
        try {
            JsonNode response = restClient.put()
                    .uri(URI.create(properties.baseUrl() + path + "?" + query))
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
                    .header("X-Signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return response != null ? response : objectMapper.createObjectNode();
        } catch (RestClientResponseException e) {
            log.error("네이버 API PUT 실패 {} -> {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("네이버 API 변경에 실패했습니다: " + e.getResponseBodyAsString());
        }
    }

    private void delete(String path, String query) {
        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "DELETE", path);
        URI uri = URI.create(properties.baseUrl() + path + (query.isEmpty() ? "" : "?" + query));
        try {
            restClient.delete()
                    .uri(uri)
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
                    .header("X-Signature", signature)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("네이버 API DELETE 실패 {} -> {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException("네이버 API 삭제에 실패했습니다: " + e.getResponseBodyAsString());
        }
    }

    /** 비즈머니 잔액. 응답: {"customerId":..., "bizmoney": 123456, "budgetLock": false, "refundLock": false} */
    @Override
    public long bizmoneyBalance() {
        try {
            Map<String, Object> body = get("/billing/bizmoney", "",
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Object value = body.get("bizmoney");
            return value instanceof Number n ? n.longValue() : -1;
        } catch (RuntimeException e) {
            log.warn("비즈머니 잔액 조회 실패: {}", e.getMessage());
            return -1;
        }
    }

    /** 리포트 다운로드 URL 은 별도 경로라 서명 경로를 그 URL 의 path 로 잡는다. */
    private String getRaw(String url) {
        URI uri = URI.create(url);
        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "GET", uri.getPath());
        try {
            String body = RestClient.create().get()
                    .uri(uri)
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
                    .header("X-Signature", signature)
                    .retrieve()
                    .body(String.class);
            return body != null ? body : "";
        } catch (RestClientResponseException e) {
            log.error("리포트 다운로드 실패 {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("리포트 다운로드에 실패했습니다: " + e.getStatusCode());
        }
    }

    private <T> T get(String path, String query, ParameterizedTypeReference<T> type) {
        NaverCredentials cred = credentials();
        long timestamp = System.currentTimeMillis();
        String signature = NaverSignature.sign(cred.secretKey(), timestamp, "GET", path);
        URI uri = URI.create(properties.baseUrl() + path + (query.isEmpty() ? "" : "?" + query));

        try {
            T body = restClient.get()
                    .uri(uri)
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-API-KEY", cred.apiKey())
                    .header("X-Customer", cred.customerId())
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
