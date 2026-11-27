package com.marketingagent.domain.subsidy.collect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.domain.subsidy.SubsidyAnnouncement.Normalized;
import com.marketingagent.domain.subsidy.SubsidyProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 기업마당 지원사업 API. 중기부·중진공·소진공·신보·기보 등 공고 대부분이 여기로 모인다.
 * {@code GET bizinfoApi.do?crtfcKey=&dataType=json&pageIndex=&pageUnit=}
 */
@Slf4j
@Component
public class BizinfoCollector implements AnnouncementCollector {

    public static final String SOURCE = "bizinfo";
    private static final String SITE = "https://www.bizinfo.go.kr";
    private static final int PAGE_UNIT = 100;

    private final SubsidyProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public BizinfoCollector(SubsidyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public boolean configured() {
        return properties.hasBizinfo();
    }

    @Override
    public List<Normalized> fetchOpen() {
        List<Normalized> result = new ArrayList<>();
        int maxPages = properties.maxPages() > 0 ? properties.maxPages() : 20;
        for (int page = 1; page <= maxPages; page++) {
            String url = properties.bizinfoUrl() + "?crtfcKey=" + enc(properties.bizinfoKey())
                    + "&dataType=json&pageUnit=" + PAGE_UNIT + "&pageIndex=" + page;
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            List<Normalized> rows = parse(body);
            result.addAll(rows);
            if (rows.size() < PAGE_UNIT) {
                break;
            }
        }
        return result;
    }

    /** 응답 문자열 → 공고 목록. 마감이 지난 공고는 뺀다. */
    List<Normalized> parse(String body) {
        List<Normalized> out = new ArrayList<>();
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("기업마당 응답을 읽지 못했습니다: " + abbreviate(body), e);
        }
        JsonNode items = root.has("jsonArray") ? root.path("jsonArray")
                : root.path("items").isArray() ? root.path("items") : root;
        if (items.isObject() && items.has("item")) {
            items = items.path("item");
        }
        LocalDate today = LocalDate.now();
        for (JsonNode n : items) {
            Normalized row = normalize(n);
            if (row != null && (row.applyEnd() == null || !row.applyEnd().isBefore(today))) {
                out.add(row);
            }
        }
        return out;
    }

    Normalized normalize(JsonNode n) {
        String id = Fields.text(n, "pblancId", "pblanc_id", "seq");
        String title = Fields.text(n, "pblancNm", "title", "pblanc_nm");
        if (id == null || title == null) {
            return null;
        }
        String period = Fields.text(n, "reqstBeginEndDe", "reqstDt", "applyPeriod");
        String agency = Fields.text(n, "jrsdInsttNm", "author", "excInsttNm");
        String exec = Fields.text(n, "excInsttNm");
        if (exec != null && agency != null && !exec.equals(agency)) {
            agency = agency + " / " + exec;
        }
        String region = Fields.text(n, "hashtags", "hashTags");
        String target = Fields.text(n, "trgetNm", "trgetNmCn", "target");
        String summary = Fields.text(n, "bsnsSumryCn", "description", "bsnsSumry");
        String url = Fields.absolute(Fields.text(n, "pblancUrl", "link", "url"), SITE);
        return new Normalized(SOURCE, id, title, agency, summary, target, region,
                Fields.date(period, 0), Fields.date(period, 1), url, n.toString());
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String s) {
        return s == null ? "null" : s.substring(0, Math.min(200, s.length()));
    }
}
