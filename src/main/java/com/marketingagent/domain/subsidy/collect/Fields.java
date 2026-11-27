package com.marketingagent.domain.subsidy.collect;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 소스 응답에서 값을 꺼내는 도구. 필드 이름이 문서와 실제가 다를 수 있어 후보 이름을 차례로 본다.
 * 실제 응답 샘플(src/test/resources/subsidy/fixtures)로 테스트를 고정한다.
 */
final class Fields {

    private static final Pattern DATE = Pattern.compile("(\\d{4})[.\\-/]?(\\d{1,2})[.\\-/]?(\\d{1,2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private Fields() {}

    static String text(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.path(n);
            if (!v.isMissingNode() && !v.isNull()) {
                String s = clean(v.isTextual() ? v.asText() : v.toString());
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    /** HTML 태그·엔티티를 지우고 공백을 정리한다. 비면 null. */
    static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = TAG.matcher(s).replaceAll(" ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
        // 공공데이터포털 일부 API 는 빈 값을 "None"·"null" 글자로 보낸다
        return t.isEmpty() || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("null") ? null : t;
    }

    /** 문자열 안의 n번째(0부터) 날짜. "2026-01-02 ~ 2026-02-03", "20260102" 등. 없으면 null. */
    static LocalDate date(String s, int index) {
        if (s == null) {
            return null;
        }
        Matcher m = DATE.matcher(s);
        int i = 0;
        while (m.find()) {
            if (i++ == index) {
                try {
                    return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)));
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    static String absolute(String url, String base) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("http")) {
            return url;
        }
        return base + (url.startsWith("/") ? "" : "/") + url;
    }

    static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
}
