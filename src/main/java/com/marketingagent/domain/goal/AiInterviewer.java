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
 * <p>모델에게 맡기는 일은 두 가지뿐이다. 사용자의 말에서 사업 구조 값을 읽어 내는 것과, 다음에 물을 말을 쉬운 말로 쓰는 것.
 * 예산·CPA·기대 성과 같은 숫자는 모델이 말하지 않는다. 서버가 계산해서 따로 보여 준다.
 * 모델이 돌려준 값은 범위를 검사한 뒤에만 받아들이고, 응답을 읽을 수 없으면 비워서 돌려준다(정해진 질문으로 이어 간다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInterviewer {

    private static final int HISTORY_LIMIT = 12;

    private static final String SYSTEM_PROMPT = """
            너는 네이버 검색광고를 처음 하는 사장님을 돕는 상담원이다. 마케팅 용어를 쓰지 말고 쉬운 말로, 한 번에 한 가지만 묻는다.
            목표는 광고 계산에 필요한 사업 구조를 채우는 것이다. 업종은 무엇이든 될 수 있다.

            채울 항목(JSON 필드):
            - industry: 업종 (사용자가 쓴 그대로)
            - revenueModel: DIRECT(직접 판매·대여) | COMMISSION(중개 수수료) | SUBSCRIPTION(매달 정기 결제)
            - offerings: [{name, revenuePerDeal(원, 계약 1건 매출. 수수료면 수수료, 정기 결제면 한 달 결제액), profitPerDeal(원, 실제로 남는 돈)}]
            - leadToDealPct: 문의 100건 중 계약 건수(%)
            - dealsPerCustomer: 고객 1명이 계약하는 평균 횟수(재계약 포함, 1 이상). 정기 결제면 평균 유지 개월 수
            - goalType: REVENUE | PROFIT | DEALS | LEADS, goalAmount: 매출·이익이면 원, 계약·문의면 건수 (한 달 기준)
            - adSharePct: 계약 1건 이익 중 광고비로 쓸 비율(%)
            - budgetLimit: 한 달 광고비 상한(원). 상한이 없다고 하면 0
            - seedKeywords: 고객이 네이버에서 검색할 말 1~5개

            규칙:
            - 사용자가 말한 값만 채운다. 추측해서 채우지 않는다.
            - 사용자가 모른다고 한 항목은 unknown 목록에 필드 이름을 넣는다. 서버가 보수적인 가정으로 채우고 사용자에게 알린다.
            - 예산, CPA, 기대 문의·매출 같은 계산 결과는 절대 말하지 않는다. 그 숫자는 서버가 계산해서 화면에 따로 보여 준다.
            - reply 에는 방금 들은 내용을 짧게 확인하고, "남은 항목" 중 이번에 채우지 못한 첫 항목을 쉬운 예시와 함께 묻는다.
            - 남은 항목이 없으면 묻지 말고, 들은 내용을 확인만 한다.
            - 반드시 아래 JSON 하나만 출력한다. 다른 글을 붙이지 않는다.
            {"reply": "사용자에게 할 말", "model": {바뀐 항목만}, "unknown": ["필드 이름", ...]}
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

    private String prompt(BusinessModel current, List<GoalMessage> history, String userText, Slot nextSlot,
            String hint) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("지금까지 채운 사업 구조:\n").append(objectMapper.writeValueAsString(current)).append("\n\n");
        List<String> remaining = current.remainingItems();
        sb.append("남은 항목(묻는 순서): ").append(remaining.isEmpty() ? "없음(모두 채움)" : String.join(", ", remaining))
                .append("\n첫 항목을 묻는 기본 문장(참고): ").append(hint == null ? "" : hint).append("\n\n");
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
