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

    public boolean configured() {
        return properties.shopConfigured();
    }

    /** 상품명으로 찾아 링크의 상품번호와 같은 것을, 없으면 이름이 가장 비슷한 것을 고른다. 못 찾으면 null. */
    public ShopItem find(String productName, String linkUrl) {
        if (!configured()) {
            return null;
        }
        try {
            String url = properties.shopUrl() + "?display=20&query="
                    + URLEncoder.encode(productName, StandardCharsets.UTF_8);
            String body = restClient.get().uri(URI.create(url))
                    .header("X-Naver-Client-Id", properties.naverClientId())
                    .header("X-Naver-Client-Secret", properties.naverClientSecret())
                    .retrieve().body(String.class);
            return pick(parse(objectMapper, body), productName, linkUrl);
        } catch (Exception e) {
            log.warn("네이버 쇼핑 검색 실패: {} {}", productName, e.getMessage());
            return null;
        }
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
