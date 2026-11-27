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
 * K-Startup 지원사업 공고(공공데이터포털 15125364, 창업진흥원). 모집 중(rcrt_prgs_yn=Y)만 받는다.
 * 지역 조건이 서버에서 안 걸리는 사례가 있어 지역 필터는 매칭 단계에서 다시 본다.
 */
@Slf4j
@Component
public class KstartupCollector implements AnnouncementCollector {

    public static final String SOURCE = "kstartup";
    private static final String SITE = "https://www.k-startup.go.kr";
    private static final int PER_PAGE = 100;

    private final SubsidyProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public KstartupCollector(SubsidyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public boolean configured() {
        return properties.hasKstartup();
    }

    @Override
    public List<Normalized> fetchOpen() {
        List<Normalized> result = new ArrayList<>();
        int maxPages = properties.maxPages() > 0 ? properties.maxPages() : 20;
        for (int page = 1; page <= maxPages; page++) {
            String url = properties.kstartupUrl() + "?serviceKey=" + enc(properties.dataGoKrKey())
                    + "&page=" + page + "&perPage=" + PER_PAGE + "&returnType=json"
                    + "&" + enc("cond[rcrt_prgs_yn::EQ]") + "=Y";
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            List<Normalized> rows = parse(body);
            result.addAll(rows);
            if (rows.size() < PER_PAGE) {
                break;
            }
        }
        return result;
    }

    List<Normalized> parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("K-Startup 응답을 읽지 못했습니다: "
                    + (body == null ? "null" : body.substring(0, Math.min(200, body.length()))), e);
        }
        JsonNode items = root.has("data") ? root.path("data") : root.path("response").path("body").path("items");
        if (items.isObject() && items.has("item")) {
            items = items.path("item");
        }
        List<Normalized> out = new ArrayList<>();
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
        String id = Fields.text(n, "pbanc_sn", "pbancSn", "id");
        String title = Fields.text(n, "biz_pbanc_nm", "intg_pbanc_biz_nm", "bizPbancNm", "title");
        if (id == null || title == null) {
            return null;
        }
        String agency = Fields.text(n, "pbanc_ntrp_nm", "sprv_inst", "pbancNtrpNm");
        String support = Fields.text(n, "support_ctnt");
        String summary = joinNonNull(Fields.text(n, "pbanc_ctnt", "pbancCtnt"), support != null ? "지원 내용: " + support : null);
        String excluded = Fields.text(n, "aply_excl_trgt_ctnt");
        String field = Fields.text(n, "supt_biz_clsfc");
        String target = joinNonNull(Fields.text(n, "aply_trgt_ctnt", "aplyTrgtCtnt"), Fields.text(n, "aply_trgt"),
                excluded != null ? "제외 대상: " + excluded : null, field != null ? "분야: " + field : null,
                Fields.text(n, "biz_enyy") != null ? "업력: " + Fields.text(n, "biz_enyy") : null,
                Fields.text(n, "biz_trgt_age") != null ? "대상 연령: " + Fields.text(n, "biz_trgt_age") : null);
        String region = Fields.text(n, "supt_regin", "suptRegin");
        LocalDate start = Fields.date(Fields.text(n, "pbanc_rcpt_bgng_dt", "pbancRcptBgngDt"), 0);
        LocalDate end = Fields.date(Fields.text(n, "pbanc_rcpt_end_dt", "pbancRcptEndDt"), 0);
        String url = Fields.absolute(Fields.text(n, "detl_pg_url", "detlPgUrl", "biz_aply_url"), SITE);
        return new Normalized(SOURCE, id, title, agency, summary, target, region, start, end, url, n.toString());
    }

    private static String joinNonNull(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null) {
                if (!sb.isEmpty()) {
                    sb.append(" / ");
                }
                sb.append(p);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
