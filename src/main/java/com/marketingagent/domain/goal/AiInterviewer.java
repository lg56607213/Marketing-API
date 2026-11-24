package com.marketingagent.domain.goal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.domain.goal.BusinessModel.Offering;
import com.marketingagent.domain.goal.BusinessModel.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 모델이 사람처럼 대화하면서 사업 구조를 채우게 한다.
 *
 * <p>모델에게 맡기는 일은 세 가지다. 사용자의 긴 설명에서 사업 구조 값을 한꺼번에 읽어 내는 것, 빠진 것만 묶어서 묻는 것,
 * 그리고 서버가 계산한 숫자로 종합 제안을 쓰는 것. 예산·CPA·기대 성과 같은 숫자는 모델이 만들지 않는다.
 * 모델이 돌려준 값은 범위를 검사한 뒤에만 받아들이고, 응답을 읽을 수 없으면 비워서 돌려준다(정해진 질문으로 이어 간다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInterviewer {

    private static final int HISTORY_LIMIT = 12;

    private static final String SYSTEM_PROMPT = """
            너는 네이버 검색광고를 처음 하는 사장님을 돕는 광고 상담원이다. 마케팅 용어 대신 쉬운 말을 쓴다.
            일문일답으로 하나씩 캐묻지 않는다. 사장님이 편하게 길게 말한 내용에서 필요한 값을 한꺼번에 추려 내고,
            꼭 필요한데 빠진 것만 한 번에 묶어서 묻는다. 업종은 무엇이든 될 수 있다.

            채울 항목(JSON 필드):
            - industry: 업종 (한 줄로 짧게 정리)
            - revenueModel: DIRECT(직접 판매·대여) | COMMISSION(중개 수수료) | SUBSCRIPTION(매달 정기 결제)
            - offerings: [{name, revenuePerDeal(원, 계약 1건 매출. 수수료면 수수료, 정기 결제면 한 달 결제액),
                           profitPerDeal(원, 실제로 남는 돈), sharePct(전체 계약 중 이 상품 비중 %)}]
            - leadToDealPct: 문의 100건 중 계약 건수(%)
            - dealsPerCustomer: 고객 1명이 계약하는 평균 횟수(재계약 포함, 1 이상). 정기 결제면 평균 유지 개월 수
            - goalType: REVENUE | PROFIT | DEALS | LEADS, goalAmount: 매출·이익이면 원, 계약·문의면 건수 (한 달 기준)
            - adSharePct: 계약 1건 이익 중 광고비로 쓸 비율(%)
            - budgetLimit: 한 달 광고비 상한(원). 상한이 없다고 하면 0
            - segments: 주 고객층 목록 (예: ["법인", "화물차 사업자", "저신용 개인"])
            - concerns: 사장님이 말한 지금 상황·고민을 한두 문장으로 추린 것
            - keywordGroups: [{name: "고객층 · 검색 의도", keywords: [...]}] 3~6묶음, 묶음마다 키워드 2~4개
            - seedKeywords: keywordGroups 중 가장 중요한 키워드 1~5개
            - websites: 사장님 회사의 홈페이지 주소 목록 (말한 것만)
            - competitors: [{name, url}] 사장님이 말한 경쟁 업체·따라 하고 싶은 업체 (말한 것만, 주소를 모르면 name 만)

            키워드 규칙:
            - "중고화물차"처럼 넓은 한 단어만 쓰지 않는다. 고객층과 검색 의도(견적·가격·조건·비교·승인·지역 등)를 붙인
              구매 직전의 검색어를 고른다. 예: "법인 장기렌트 견적", "1톤 화물차 리스 조건", "저신용 장기렌트 승인".
            - 사장님이 말한 고객층마다 묶음을 만든다. 사장님이 키워드를 직접 말하면 그것도 넣는다.

            대화 규칙:
            - 사용자가 말한 값만 채운다. 숫자를 추측해서 채우지 않는다. 키워드·고객층·고민은 말한 내용에서 정리해도 된다.
            - 사용자가 모른다고 한 항목은 unknown 목록에 필드 이름을 넣는다. 서버가 보수적인 가정으로 채운다.
            - 예산, CPA, 기대 문의·매출 같은 계산 결과는 절대 말하지 않는다. 그 숫자는 서버가 계산한다.
            - reply: 먼저 들은 내용을 2~3줄로 정리해 보여 주고, "꼭 필요한데 빠진 항목"이 있으면 그것들을
              한 메시지에 번호 목록으로 묶어 예시와 함께 묻는다(최대 3개). 나머지 빠진 항목은 묻지 말고
              "모르시는 건 업종 평균으로 잡고 운영하면서 맞추겠다"고 한 줄로 알린다.
            - 꼭 필요한데 빠진 항목이 없으면 묻지 말고 정리만 한 뒤 "이 내용으로 계산과 키워드 조사를 하겠다"고 말한다.
            - 마크다운 기호(#, **, 표)는 쓰지 않는다. 목록은 "1)" 또는 "•" 로 쓴다.
            - 반드시 아래 JSON 하나만 출력한다. 다른 글을 붙이지 않는다.
            {"reply": "사용자에게 할 말", "model": {바뀐 항목만}, "unknown": ["필드 이름", ...]}
            """;

    private static final String SOLUTION_PROMPT = """
            너는 네이버 검색광고 컨설턴트다. 사장님의 사업 구조와 서버가 계산·조사한 숫자를 받아,
            사장님이 바로 실행할 수 있는 종합 제안을 쓴다.

            규칙:
            - 숫자는 주어진 자료에 있는 것만 그대로 쓴다. 새 숫자를 만들거나 바꾸지 않는다. 자료에 없으면 숫자 없이 말한다.
            - 쉬운 말로, 1,200자 안쪽으로 쓴다. 마크다운 기호(#, **, 표)는 쓰지 않는다.
            - 아래 순서와 제목을 그대로 쓰고, 제목 줄은 "■ " 로 시작하고 내용은 "• " 목록으로 쓴다.
            ■ 한 줄 진단 — 목표 CPA(첫 계약 기준)와 시장 예상 CPA 를 비교해 지금 상황을 한 문장으로.
            ■ 고객층별 전략 — 고객층마다 어떤 키워드 묶음을, 몇 위 정도로(1·3·5위 입찰가 참고) 걸지.
            ■ 먼저 쓸 키워드 — 판정이 '목표 이내'인 키워드부터 3~5개를 입찰가와 함께. 없으면 가장 싼 것부터 낮은 순위로.
            ■ 예산 배분 — 추천 운영안의 한 달 예산을 기준으로 고객층별로 어떻게 나눌지(비율은 말해도 되지만 새 금액은 만들지 않는다).
            ■ 지금 광고의 문제점과 개선 — '실적진단'이 있으면 문제점·개선점을 근거 숫자와 함께 중요한 순서로 3~5개.
              새 키워드 후보·제외 키워드 후보가 있으면 이름을 든다. '실적진단'이 없으면 이 제목은 뺀다.
            ■ 경쟁사·선도 업체에서 배울 광고 방법 — '경쟁사분석'의 권유마다 "OO은(는) 홈페이지의 OO 흔적으로 보아 OO 광고를
              하고 있는 것으로 추정되며, 이 채널은 보통 OO 효과를 노립니다. 사장님도 OO을 작은 예산으로 시험해 보세요" 형식으로 쓴다.
              흔적이 없는 채널은 업체가 쓴다고 말하지 않는다. 업계에서 흔히 쓰는 방법을 덧붙일 때는 "(일반적인 업계 관행, 근거 수치 없음)"을 붙인다.
              '경쟁사분석'이 없으면 업종에 맞는 방법 1~2개를 "(일반적인 업계 관행, 근거 수치 없음)"으로만 쓴다.
            ■ 전환을 올릴 방법 — 랜딩 페이지·상담 응대 등 문의 1건 비용을 낮출 실행 항목 2~3개.
            ■ 첫 2주 할 일 — 체크리스트 3~4개.
            - 경쟁사의 광고비·성과는 어디에도 공개되지 않으므로 숫자로 말하지 않는다. "추정"이라고 쓴다.
            - 결과는 제안 본문만 출력한다.
            """;

    private static final String COMPETITOR_PROMPT = """
            너는 한국 시장에 밝은 마케팅 리서처다. 주어진 업종·고객층에서 한국 시장의 대표적인 선도 업체를 최대 3곳 고른다.
            - 실제로 존재한다고 확신하는 업체만 쓴다. 확신이 없으면 빈 목록을 준다.
            - url 은 그 업체의 공식 홈페이지 주소를 알고 있을 때만 쓰고, 모르면 빈 문자열로 둔다.
            - 사장님 회사 자신은 넣지 않는다.
            - 반드시 아래 JSON 하나만 출력한다.
            {"competitors": [{"name": "업체 이름", "url": "https://..."}]}
            """;


    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;

    /**
     * @param model     바뀐 사업 구조
     * @param reply     모델이 쓴 다음 말. 비어 있으면 정해진 질문을 쓴다
     * @param unknown   사용자가 모른다고 한 항목
     */
    public record Turn(BusinessModel model, String reply, List<String> unknown) {}

    public Optional<Turn> next(BusinessModel current, List<GoalMessage> history, String userText, Slot nextSlot,
            String nextQuestionHint) {
        try {
            String prompt = prompt(current, history, userText, nextSlot, nextQuestionHint);
            String raw = aiProvider.generate(new AiRequest(SYSTEM_PROMPT, prompt, null)).content();
            JsonNode json = objectMapper.readTree(extractJson(raw));
            BusinessModel merged = merge(current, json.path("model"));
            List<String> unknown = new ArrayList<>();
            json.path("unknown").forEach(node -> unknown.add(node.asText()));
            String reply = json.path("reply").asText("").trim();
            return Optional.of(new Turn(merged, reply.isEmpty() ? null : reply, unknown));
        } catch (Exception e) {
            log.warn("AI 상담 응답을 쓰지 못해 정해진 질문으로 이어 갑니다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 서버가 계산한 숫자로 종합 제안을 쓴다. 숫자는 facts 에 있는 것만 쓰게 한다. 실패하면 비워서 돌려준다.
     *
     * @param facts 사업 구조와 계산·조사 결과를 담은 JSON
     */
    public Optional<String> solution(String facts) {
        try {
            String raw = aiProvider.generate(new AiRequest(SOLUTION_PROMPT, "자료:\n" + facts, null)).content();
            String text = raw == null ? "" : raw.strip();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("AI 종합 제안을 쓰지 못해 정해진 문장으로 대신합니다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 경쟁사를 듣지 못했을 때 업계 선도 업체를 추정한다. 결과는 출처를 AI 로 표시하고, 주소는 서버가 실제로 열어 확인한다.
     */
    public List<BusinessModel.Competitor> suggestCompetitors(BusinessModel model) {
        try {
            String input = String.format("업종: %s%n고객층: %s%n상품: %s%n사장님 홈페이지: %s", model.getIndustry(),
                    model.getSegments(), model.getOfferings().stream().map(Offering::getName).toList(), model.getWebsites());
            String raw = aiProvider.generate(new AiRequest(COMPETITOR_PROMPT, input, null)).content();
            JsonNode json = objectMapper.readTree(extractJson(raw));
            return competitors(json.path("competitors"), "AI");
        } catch (Exception e) {
            log.warn("업계 선도 업체를 추정하지 못했습니다: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<BusinessModel.Competitor> competitors(JsonNode array, String source) {
        List<BusinessModel.Competitor> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            String name = node.path("name").asText("").trim();
            String url = node.path("url").asText("").trim();
            if (name.isEmpty() || name.length() > 50 || result.size() >= 4) {
                continue;
            }
            if (!url.isEmpty() && (url.length() > 200 || !url.matches("(?i)^(https?://)?[a-z0-9.-]+\\.[a-z]{2,}(/\\S*)?$"))) {
                url = "";
            }
            String from = "AI".equals(node.path("source").asText()) ? "AI" : source;
            result.add(new BusinessModel.Competitor(name, url.isEmpty() ? null : url, from));
        }
        return result;
    }

    private String prompt(BusinessModel current, List<GoalMessage> history, String userText, Slot nextSlot,
            String hint) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("지금까지 채운 사업 구조:\n").append(objectMapper.writeValueAsString(current)).append("\n\n");
        List<String> critical = current.missingCritical();
        List<String> remaining = new ArrayList<>(current.remainingItems());
        remaining.removeAll(critical);
        sb.append("꼭 필요한데 빠진 항목: ").append(critical.isEmpty() ? "없음" : String.join(", ", critical)).append('\n')
                .append("빠졌지만 가정으로 채울 수 있는 항목: ").append(remaining.isEmpty() ? "없음" : String.join(", ", remaining))
                .append("\n\n");
        sb.append("최근 대화:\n");
        List<GoalMessage> recent = history.subList(Math.max(0, history.size() - HISTORY_LIMIT), history.size());
        for (GoalMessage m : recent) {
            sb.append(GoalMessage.USER.equals(m.getRole()) ? "사용자: " : "상담원: ").append(m.getContent()).append('\n');
        }
        sb.append("\n사용자의 새 말: ").append(userText);
        return sb.toString();
    }

    /** 모델이 코드 블록이나 설명을 붙여도 첫 JSON 객체만 꺼낸다. */
    static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("JSON 이 없습니다");
        }
        return raw.substring(start, end + 1);
    }

    /** 모델이 준 값을 범위 검사를 거쳐 현재 구조에 얹는다. 이상한 값은 버린다. */
    BusinessModel merge(BusinessModel current, JsonNode patch) throws Exception {
        BusinessModel m = objectMapper.readValue(objectMapper.writeValueAsString(current), BusinessModel.class);
        if (patch == null || !patch.isObject()) {
            return m;
        }
        ObjectNode p = (ObjectNode) patch;
        text(p, "industry", 100).ifPresent(m::setIndustry);
        text(p, "revenueModel", 20).flatMap(v -> enumValue(BusinessModel.RevenueModel.class, v)).ifPresent(m::setRevenueModel);
        if (p.has("offerings") && p.get("offerings").isArray()) {
            List<Offering> offerings = new ArrayList<>();
            for (JsonNode node : p.get("offerings")) {
                String name = node.path("name").asText("").trim();
                if (name.isEmpty() || offerings.size() >= 5) {
                    continue;
                }
                Offering o = existing(m.getOfferings(), name);
                positiveLong(node, "revenuePerDeal").ifPresent(o::setRevenuePerDeal);
                percent(node, "sharePct").ifPresent(o::setSharePct);
                positiveLong(node, "profitPerDeal").ifPresent(profit -> {
                    if (o.getRevenuePerDeal() == null || profit <= o.getRevenuePerDeal()) {
                        o.setProfitPerDeal(profit);
                        o.setProfitSource(BusinessModel.SOURCE_USER);
                    }
                });
                offerings.add(o);
            }
            if (!offerings.isEmpty()) {
                m.setOfferings(offerings);
            }
        }
        percent(p, "leadToDealPct").ifPresent(v -> {
            m.setLeadToDealPct(v);
            m.setLeadToDealSource(BusinessModel.SOURCE_USER);
        });
        positiveDouble(p, "dealsPerCustomer").filter(v -> v >= 1 && v <= 120).ifPresent(v -> {
            m.setDealsPerCustomer(v);
            m.setDealsPerCustomerSource(BusinessModel.SOURCE_USER);
        });
        Optional<BusinessModel.GoalType> goalType = text(p, "goalType", 20)
                .flatMap(v -> enumValue(BusinessModel.GoalType.class, v));
        Optional<Long> goalAmount = positiveLong(p, "goalAmount");
        if (goalType.isPresent() && goalAmount.isPresent()) {
            m.setGoalType(goalType.get());
            m.setGoalAmount(goalAmount.get());
        }
        percent(p, "adSharePct").ifPresent(v -> {
            m.setAdSharePct(v);
            m.setAdShareSource(BusinessModel.SOURCE_USER);
        });
        if (p.has("budgetLimit") && p.get("budgetLimit").isNumber() && p.get("budgetLimit").asLong() >= 0) {
            m.setBudgetLimit(p.get("budgetLimit").asLong());
        }
        List<String> segments = strings(p.get("segments"), 6, 30);
        if (!segments.isEmpty()) {
            m.setSegments(segments);
        }
        text(p, "concerns", 300).ifPresent(m::setConcerns);
        List<String> websites = strings(p.get("websites"), 3, 200).stream()
                .filter(u -> u.matches("(?i)^(https?://)?[a-z0-9.-]+\\.[a-z]{2,}(/\\S*)?$")).toList();
        if (!websites.isEmpty()) {
            m.setWebsites(new ArrayList<>(websites));
        }
        List<BusinessModel.Competitor> rivals = competitors(p.get("competitors"), BusinessModel.SOURCE_USER);
        if (!rivals.isEmpty()) {
            m.setCompetitors(new ArrayList<>(rivals));
        }
        if (p.has("keywordGroups") && p.get("keywordGroups").isArray()) {
            List<BusinessModel.KeywordGroup> groups = new ArrayList<>();
            for (JsonNode node : p.get("keywordGroups")) {
                String name = node.path("name").asText("").trim();
                List<String> words = strings(node.get("keywords"), 5, 50);
                if (!name.isEmpty() && !words.isEmpty() && groups.size() < 6) {
                    groups.add(new BusinessModel.KeywordGroup(name.length() > 40 ? name.substring(0, 40) : name, words));
                }
            }
            if (!groups.isEmpty()) {
                m.setKeywordGroups(groups);
            }
        }
        if (p.has("seedKeywords") && p.get("seedKeywords").isArray()) {
            List<String> keywords = new ArrayList<>();
            p.get("seedKeywords").forEach(k -> {
                String v = k.asText("").trim();
                if (!v.isEmpty() && v.length() <= 50 && keywords.size() < 5 && !keywords.contains(v)) {
                    keywords.add(v);
                }
            });
            if (!keywords.isEmpty()) {
                m.setSeedKeywords(keywords);
            }
        }
        return m;
    }

    private static List<String> strings(JsonNode array, int max, int maxLength) {
        List<String> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        array.forEach(n -> {
            String v = n.asText("").trim();
            if (!v.isEmpty() && v.length() <= maxLength && result.size() < max && !result.contains(v)) {
                result.add(v);
            }
        });
        return result;
    }

    private static Offering existing(List<Offering> offerings, String name) {
        if (offerings != null) {
            for (Offering o : offerings) {
                if (name.equals(o.getName())) {
                    return o;
                }
            }
        }
        return new Offering(name.length() > 50 ? name.substring(0, 50) : name);
    }

    private static Optional<String> text(JsonNode p, String field, int max) {
        JsonNode node = p.get(field);
        if (node == null || !node.isTextual()) {
            return Optional.empty();
        }
        String v = node.asText().trim();
        return v.isEmpty() ? Optional.empty() : Optional.of(v.length() > max ? v.substring(0, max) : v);
    }

    private static Optional<Long> positiveLong(JsonNode p, String field) {
        JsonNode node = p.get(field);
        return node != null && node.isNumber() && node.asLong() > 0 ? Optional.of(node.asLong()) : Optional.empty();
    }

    private static Optional<Double> positiveDouble(JsonNode p, String field) {
        JsonNode node = p.get(field);
        return node != null && node.isNumber() && node.asDouble() > 0 ? Optional.of(node.asDouble()) : Optional.empty();
    }

    private static Optional<Double> percent(JsonNode p, String field) {
        return positiveDouble(p, field).filter(v -> v <= 100);
    }

    private static <E extends Enum<E>> Optional<E> enumValue(Class<E> type, String value) {
        try {
            return Optional.of(Enum.valueOf(type, value.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
