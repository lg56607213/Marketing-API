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
 * 판판대로(한국중소벤처기업유통원) 지원사업 정보(공공데이터포털 15163956). 유통·판로 지원이 많다.
 * 연도(sprtBizYr)가 필수이고 하루 호출 한도가 100회라, 올해(12월이면 내년도 함께)를 한 번에 크게 받는다.
 */
@Slf4j
@Component
public class FanfandaeroCollector implements AnnouncementCollector {

    public static final String SOURCE = "fanfandaero";
    private static final int ROWS = 500;

    private final SubsidyProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public FanfandaeroCollector(SubsidyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public boolean configured() {
        return properties.hasKstartup() && properties.fanfandaeroUrl() != null && !properties.fanfandaeroUrl().isBlank();
    }

    @Override
    public List<Normalized> fetchOpen() {
        LocalDate today = LocalDate.now();
        List<Integer> years = today.getMonthValue() == 12 ? List.of(today.getYear(), today.getYear() + 1) : List.of(today.getYear());
        List<Normalized> result = new ArrayList<>();
        for (int year : years) {
            for (int page = 1; page <= 5; page++) {
                String url = properties.fanfandaeroUrl() + "?serviceKey=" + enc(properties.dataGoKrKey())
                        + "&type=json&numOfRows=" + ROWS + "&pageNo=" + page + "&sprtBizYr=" + year;
                String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
                Page p = parse(body);
                result.addAll(p.rows());
                if (p.fetched() < ROWS) {
                    break;
                }
            }
        }
        return result;
    }

    record Page(List<Normalized> rows, int fetched) {}

    Page parse(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("판판대로 응답을 읽지 못했습니다: "
                    + (body == null ? "null" : body.substring(0, Math.min(200, body.length()))), e);
        }
        if (!"00".equals(root.path("resultCode").asText("00"))) {
            throw new IllegalStateException("판판대로 오류: " + root.path("resultMsg").asText());
        }
        JsonNode items = root.path("item");
        if (items.isObject() && items.has("item")) {
            items = items.path("item");
        }
        List<Normalized> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int fetched = 0;
        for (JsonNode n : items) {
            fetched++;
            Normalized row = normalize(n);
            if (row != null && (row.applyEnd() == null || !row.applyEnd().isBefore(today))) {
                out.add(row);
            }
        }
        return new Page(out, fetched);
    }

    Normalized normalize(JsonNode n) {
        String id = Fields.text(n, "sprtBizCd");
        String title = Fields.text(n, "sprtBizNm", "sprtBizCgDtlNm");
        if (id == null || title == null) {
            return null;
        }
        String category = join(" > ", Fields.text(n, "sprtBizCg1Nm"), Fields.text(n, "sprtBizCg3Nm"));
        String type = Fields.text(n, "sprtBizTyNm");
        String status = Fields.text(n, "progrsSttusNm");
        String summary = join(" / ", Fields.text(n, "sprtBizPurpose"), type != null ? "지원 유형: " + type : null,
                status != null ? "진행: " + status : null);
        String target = join(" / ", Fields.text(n, "sprtBizTrgtNm"), category != null ? "분야: " + category : null);
        return new Normalized(SOURCE, id, title, Fields.text(n, "operInstNm"), summary, target,
                Fields.text(n, "sprtBizCtpvNm"), Fields.date(Fields.text(n, "rcritBgngYmd"), 0),
                Fields.date(Fields.text(n, "rcritEndYmd"), 0), Fields.text(n, "sprtBizUrl"), n.toString());
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null) {
                if (!sb.isEmpty()) {
                    sb.append(sep);
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
