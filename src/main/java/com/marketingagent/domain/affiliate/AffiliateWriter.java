package com.marketingagent.domain.affiliate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.domain.affiliate.NaverShopClient.ShopItem;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 확인된 상품 정보와 사용자가 준 설명·후기만으로 네이버 블로그 글을 쓴다.
 * 광고 표시 문구와 판매 링크는 AI 에 맡기지 않고 코드가 넣는다(빠뜨리거나 바꾸지 않게).
 */
@Component
public class AffiliateWriter {

    /** 공정위 추천·보증 심사지침: 경제적 이해관계를 글 첫머리에 분명히 적는다. */
    public static final String DISCLOSURE =
            "이 포스팅은 네이버 쇼핑 커넥트(브랜드커넥트) 활동의 일환으로, 판매 발생 시 수수료를 제공받습니다.";

    private static final String SYSTEM = """
            너는 네이버 블로그에 상품 소개 글을 쓰는 작가다. 읽는 사람이 살지 말지 판단하도록 돕는 정직한 글을 쓴다.
            절대 규칙:
            1. [확인된 상품 정보]와 [판매자 설명]에 있는 사실만 쓴다. 성분·스펙·효능·수치·혜택을 지어내지 않는다.
               모르는 내용은 "상세페이지에서 확인해 보세요"라고 쓴다.
            2. [내 후기]가 없으면 직접 써 본 것처럼 쓰지 않는다("써 보니", "직접 먹어 보니" 금지).
               이때는 "이런 분께 맞겠다", "고를 때 볼 점"처럼 정보·비교 중심으로 쓴다.
               [내 후기]가 있으면 그 내용만 1인칭 경험으로 살려 쓴다. 후기를 부풀리지 않는다.
            3. 의약품처럼 병을 고친다, 최고·1위·무조건 같은 단정·과장 표현을 쓰지 않는다.
            4. 가격은 "작성일 기준"이라고 붙이고, 바뀔 수 있다고 적는다.
            5. 광고 표시 문구와 구매 링크는 시스템이 따로 넣으니 본문에 쓰지 않는다.
            글 모양:
            - 제목: 검색 키워드를 앞쪽에 넣고 30자 안팎. 낚시성 제목 금지
            - 섹션 4~6개, 섹션마다 소제목 + 본문 2~4문단. 한 문단은 2~3문장. 모바일에서 읽기 쉽게 짧게
            - 전체 1,500~2,500자. 친근한 존댓말
            - 사진 자리: 섹션마다 photo 에 넣을 사진 번호를 하나 고르거나 null.
              쓸 수 있는 사진은 [사진 목록]에 있는 번호만. 같은 사진을 두 번 쓰지 않는다
            - tags: 네이버 블로그 태그 8~10개(# 없이)
            출력은 JSON 하나만:
            {"title":"","tags":[""],"sections":[{"heading":"","body":"","photo":"P1"}],"summary":"한 줄 요약"}
            body 의 문단 구분은 빈 줄로 한다.
            """;

    private final AiProvider aiProvider;
    private final AffiliateProperties properties;
    private final ObjectMapper objectMapper;

    public AffiliateWriter(AiProvider aiProvider, AffiliateProperties properties, ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record Section(String heading, String body, String photo) {}

    public record Draft(String title, List<String> tags, List<Section> sections, String summary) {}

    /**
     * @param photos 사진 번호 → 설명. P1 은 상품 대표 사진, M1.. 은 사용자가 올린 사진
     */
    public Draft write(String productName, ShopItem item, String notes, String review, List<String> myPhotoCaptions,
                       String keyword) throws Exception {
        String user = userPrompt(productName, item, notes, review, myPhotoCaptions, keyword, LocalDate.now());
        String raw = aiProvider.generate(new AiRequest(SYSTEM, user, properties.writeModel())).content();
        return parse(objectMapper, raw, allowedPhotos(item, myPhotoCaptions));
    }

    static String userPrompt(String productName, ShopItem item, String notes, String review, List<String> captions,
                             String keyword, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("[상품명] ").append(productName).append('\n');
        sb.append("[검색 키워드] ").append(blank(keyword) ? productName : keyword).append('\n');
        sb.append("[작성일] ").append(today).append("\n\n[확인된 상품 정보 · 네이버 쇼핑]\n");
        if (item == null) {
            sb.append("(확인하지 못함. 아래 판매자 설명만 사실로 쓴다)\n");
        } else {
            line(sb, "상품명", item.title());
            line(sb, "최저가", item.lowPrice() == null ? null
                    : NumberFormat.getInstance(Locale.KOREA).format(item.lowPrice()) + "원 (작성일 기준)");
            line(sb, "판매처", item.mallName());
            line(sb, "브랜드", item.brand());
            line(sb, "제조사", item.maker());
            line(sb, "분류", item.category());
        }
        sb.append("\n[판매자 설명 · 상세페이지에서 옮긴 내용]\n").append(blank(notes) ? "(없음)" : cut(notes, 8000));
        sb.append("\n\n[내 후기]\n").append(blank(review) ? "(없음 — 체험담 금지)" : cut(review, 4000));
        sb.append("\n\n[사진 목록]\n");
        if (item != null && item.image() != null) {
            sb.append("P1: 상품 대표 사진\n");
        }
        for (int i = 0; i < captions.size(); i++) {
            sb.append("M").append(i + 1).append(": 내가 찍은 사진 — ")
                    .append(blank(captions.get(i)) ? "설명 없음" : captions.get(i)).append('\n');
        }
        if ((item == null || item.image() == null) && captions.isEmpty()) {
            sb.append("(없음 — photo 는 모두 null)\n");
        }
        return sb.toString();
    }

    static List<String> allowedPhotos(ShopItem item, List<String> captions) {
        List<String> ids = new ArrayList<>();
        if (item != null && item.image() != null) {
            ids.add("P1");
        }
        for (int i = 0; i < captions.size(); i++) {
            ids.add("M" + (i + 1));
        }
        return ids;
    }

    /** AI 가 없는 사진 번호를 쓰거나 같은 사진을 두 번 쓰면 지운다. 안 쓴 사진은 빈 섹션 자리에 채운다. */
    static Draft parse(ObjectMapper om, String raw, List<String> allowed) throws Exception {
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        if (s < 0 || e <= s) {
            throw new IllegalStateException("AI 응답에 JSON 이 없습니다");
        }
        JsonNode n = om.readTree(raw.substring(s, e + 1));
        String title = n.path("title").asText("").trim();
        if (title.isEmpty()) {
            throw new IllegalStateException("AI 가 제목을 쓰지 않았습니다");
        }
        List<String> tags = new ArrayList<>();
        n.path("tags").forEach(t -> {
            String v = t.asText("").replace("#", "").trim();
            if (!v.isEmpty() && !tags.contains(v) && tags.size() < 10) {
                tags.add(v);
            }
        });
        List<String> unused = new ArrayList<>(allowed);
        List<Section> sections = new ArrayList<>();
        for (JsonNode x : n.path("sections")) {
            String photo = x.path("photo").isTextual() ? x.path("photo").asText().trim().toUpperCase() : null;
            if (photo != null && !unused.remove(photo)) {
                photo = null;
            }
            String body = x.path("body").asText("").trim();
            if (!body.isEmpty()) {
                sections.add(new Section(x.path("heading").asText("").trim(), body, photo));
            }
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException("AI 가 본문을 쓰지 않았습니다");
        }
        for (int i = 0; i < sections.size() && !unused.isEmpty(); i++) {
            Section sec = sections.get(i);
            if (sec.photo() == null) {
                sections.set(i, new Section(sec.heading(), sec.body(), unused.remove(0)));
            }
        }
        return new Draft(title, tags, sections, n.path("summary").asText("").trim());
    }

    private static void line(StringBuilder sb, String k, String v) {
        if (!blank(v)) {
            sb.append(k).append(": ").append(v).append('\n');
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String cut(String s, int max) {
        return s.substring(0, Math.min(max, s.length()));
    }
}
