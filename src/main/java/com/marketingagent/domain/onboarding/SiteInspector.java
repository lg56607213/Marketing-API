package com.marketingagent.domain.onboarding;

import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.onboarding.OnboardingDtos.SiteCheck;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 회원이 적은 홈페이지를 한 번 열어 보고, 광고를 시작하기 전에 알아야 할 것을 확인한다.
 * 가장 중요한 것은 전환 추적이 설치돼 있는지다. 없으면 광고 효과를 잴 수 없다.
 *
 * <p>회원이 준 주소를 서버가 직접 여는 것이므로, 서버가 있는 내부망을 들여다보는 데 악용되지 않게 막는다.
 * http/https 의 기본 포트만, 공인 주소로 풀리는 호스트만 열고, 다른 주소로 넘기는 응답은 한 번만 같은 검사를 거쳐 따라간다.
 */
@Slf4j
@Component
public class SiteInspector {

    private static final int MAX_BYTES = 400_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "<meta[^>]+name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESCRIPTION_REVERSED = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*name=[\"']description[\"']", Pattern.CASE_INSENSITIVE);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** 주소를 정리한다. 앞에 http 가 없으면 https 를 붙인다. */
    public static String normalize(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            throw new BadRequestException("홈페이지 주소를 입력하세요.");
        }
        if (!url.matches("(?i)^https?://.*")) {
            url = "https://" + url;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("홈페이지 주소가 올바르지 않습니다. 예: https://www.example.com");
        }
    }

    public SiteCheck inspect(String url) {
        List<String> notes = new ArrayList<>();
        String html;
        try {
            html = fetch(URI.create(url), true);
        } catch (BlockedAddressException e) {
            notes.add("이 주소는 확인하지 않았습니다. 외부에서 열 수 있는 공개 홈페이지 주소만 확인합니다.");
            return new SiteCheck(false, null, null, false, false, false, notes);
        } catch (Exception e) {
            log.info("홈페이지 확인 실패 {}: {}", url, e.toString());
            notes.add("홈페이지를 열어 보지 못했습니다. 주소가 맞는지, 지금 접속되는지 확인하세요. 광고를 눌렀을 때 열리지 않으면 광고비만 나갑니다.");
            return new SiteCheck(false, null, null, false, false, false, notes);
        }

        String lower = html.toLowerCase(Locale.ROOT);
        String title = clean(first(TITLE, html));
        String description = clean(first(META_DESCRIPTION, html));
        if (description == null) {
            description = clean(first(META_DESCRIPTION_REVERSED, html));
        }
        boolean naver = lower.contains("wcs.naver.net") || lower.contains("wcslog.js") || lower.contains("wcs_add");
        boolean google = lower.contains("googletagmanager.com") || lower.contains("google-analytics.com")
                || lower.contains("gtag(");
        boolean form = lower.contains("<form") || lower.contains("type=\"tel\"") || lower.contains("href=\"tel:");

        if (!naver) {
            notes.add("첫 화면에서 네이버 전환 추적 스크립트가 보이지 않습니다. 설치하지 않으면 어떤 키워드가 문의·구매로 이어졌는지 알 수 없어 "
                    + "아래 기대 효과를 확인할 방법이 없고, 자동 입찰도 동작하지 않습니다. 광고 시작 전에 설치하세요.");
        }
        if (!google && !naver) {
            notes.add("방문자 분석 도구(네이버 애널리틱스, 구글 애널리틱스)도 보이지 않습니다. 지금은 홈페이지에 몇 명이 오는지도 알 수 없는 상태입니다.");
        }
        if (!form) {
            notes.add("첫 화면에서 문의 양식이나 전화 연결 버튼이 보이지 않습니다. 광고로 들어온 사람이 바로 문의할 길이 있는지 확인하세요.");
        }
        if (title == null) {
            notes.add("페이지 제목이 없습니다. 검색 결과와 광고 품질에 불리합니다.");
        }
        notes.add("첫 화면의 소스만 확인한 결과입니다. 스크립트가 다른 방식으로 들어가 있으면 보이지 않을 수 있습니다.");
        return new SiteCheck(true, title, description, naver, google, form, notes);
    }

    private String fetch(URI uri, boolean followOnce) throws Exception {
        guard(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; site-check)")
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (status >= 300 && status < 400 && followOnce) {
                String location = response.headers().firstValue("location").orElseThrow();
                return fetch(uri.resolve(location), false);
            }
            if (status >= 400) {
                throw new IllegalStateException("HTTP " + status);
            }
            return new String(body.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
        }
    }

    /** 내부망·이 PC 자신을 가리키는 주소는 열지 않는다. */
    static void guard(URI uri) throws Exception {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BlockedAddressException();
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new BlockedAddressException();
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new BlockedAddressException();
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            byte[] raw = address.getAddress();
            boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
            boolean carrierNat = raw.length == 4 && (raw[0] & 0xff) == 100 && (raw[1] & 0xc0) == 64;
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress() || uniqueLocalV6 || carrierNat) {
                throw new BlockedAddressException();
            }
        }
    }

    private static String first(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    static class BlockedAddressException extends Exception {
    }
}
