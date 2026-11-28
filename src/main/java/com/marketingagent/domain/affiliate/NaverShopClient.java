package com.marketingagent.domain.affiliate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 쇼핑 검색 API(공식)로 상품의 실제 정보(이름·가격·브랜드·분류·대표 사진)를 확인한다.
 * 스마트스토어 상품 페이지는 서버에서 열면 차단(429)되어 쓰지 않는다.
 */
@Slf4j
@Component
public class NaverShopClient {

    private static final Pattern PRODUCT_ID = Pattern.compile("/products/(\\d{6,})");

    private final AffiliateProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public NaverShopClient(AffiliateProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record ShopItem(String title, String link, String image, Long lowPrice, String mallName, String productId,
                           String brand, String maker, String category) {}

    /** 업체가 넣은 네이버 개발자센터 검색 API 키. */
    public record Credentials(String clientId, String clientSecret) {}

    /** 서버 공용 키(설정 파일). 업체 키가 없을 때만 쓴다. 없으면 null. */
    public Credentials serverDefault() {
        return properties.shopConfigured()
                ? new Credentials(properties.naverClientId(), properties.naverClientSecret()) : null;
    }

    /** 상품명으로 찾아 링크의 상품번호와 같은 것을, 없으면 이름이 가장 비슷한 것을 고른다. 못 찾으면 null. */
    public ShopItem find(Credentials creds, String productName, String linkUrl) {
        if (creds == null) {
            return null;
        }
        try {
            return pick(parse(objectMapper, search(creds, productName)), productName, linkUrl);
        } catch (Exception e) {
            log.warn("네이버 쇼핑 검색 실패: {} {}", productName, e.getMessage());
            return null;
        }
    }

    /** 키가 맞는지 실제로 한 번 검색해 본다. 틀리면 이유를 담아 예외. */
    public void verify(Credentials creds) {
        try {
            search(creds, "텀블러");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 401) {
                throw new IllegalArgumentException("Client ID 또는 Client Secret 이 맞지 않습니다.");
            }
            if (code == 403) {
                throw new IllegalArgumentException("이 애플리케이션에 \"검색\" API 가 켜져 있지 않습니다. 개발자센터 > 내 애플리케이션 > API 설정에서 검색을 추가하세요.");
            }
            throw new IllegalArgumentException("네이버가 키 확인을 거절했습니다 (HTTP " + code + ").");
        } catch (Exception e) {
            throw new IllegalArgumentException("네이버에 연결하지 못했습니다. 잠시 뒤 다시 해 주세요.");
        }
    }

    private String search(Credentials creds, String query) {
        String url = properties.shopUrl() + "?display=20&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        return restClient.get().uri(URI.create(url))
                .header("X-Naver-Client-Id", creds.clientId())
                .header("X-Naver-Client-Secret", creds.clientSecret())
                .retrieve().body(String.class);
    }

    static List<ShopItem> parse(ObjectMapper om, String body) throws Exception {
        List<ShopItem> items = new ArrayList<>();
        for (JsonNode n : om.readTree(body).path("items")) {
            List<String> cats = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                String c = text(n, "category" + i);
                if (c != null) {
                    cats.add(c);
                }
            }
            String price = text(n, "lprice");
            items.add(new ShopItem(clean(text(n, "title")), text(n, "link"), text(n, "image"),
                    price == null ? null : Long.parseLong(price), text(n, "mallName"), text(n, "productId"),
                    text(n, "brand"), text(n, "maker"), cats.isEmpty() ? null : String.join(" > ", cats)));
        }
        return items;
    }

    static ShopItem pick(List<ShopItem> items, String productName, String linkUrl) {
        if (items.isEmpty()) {
            return null;
        }
        String id = productId(linkUrl);
        if (id != null) {
            for (ShopItem it : items) {
                if (id.equals(it.productId()) || (it.link() != null && it.link().contains(id))) {
                    return it;
                }
            }
        }
        ShopItem best = null;
        double bestScore = 0;
        for (ShopItem it : items) {
            double s = similarity(productName, it.title());
            if (s > bestScore) {
                bestScore = s;
                best = it;
            }
        }
        // 이름이 절반도 안 겹치면 다른 상품일 수 있어 쓰지 않는다(엉뚱한 정보로 글을 쓰지 않게)
        return bestScore >= 0.5 ? best : null;
    }

    static String productId(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = PRODUCT_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** 입력한 이름의 낱말 중 검색 결과 제목에 들어 있는 비율. */
    static double similarity(String want, String title) {
        if (want == null || title == null) {
            return 0;
        }
        String t = title.replace(" ", "").toLowerCase();
        String[] words = want.toLowerCase().split("\\s+");
        int total = 0;
        int hit = 0;
        for (String w : words) {
            if (w.isBlank()) {
                continue;
            }
            total++;
            if (t.contains(w)) {
                hit++;
            }
        }
        return total == 0 ? 0 : (double) hit / total;
    }

    private static String text(JsonNode n, String f) {
        String v = n.path(f).asText("");
        return v.isBlank() ? null : v;
    }

    /** 검색 결과 제목의 강조 태그와 HTML 엔티티를 없앤다. */
    static String clean(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&#39;", "'").trim();
    }
}
