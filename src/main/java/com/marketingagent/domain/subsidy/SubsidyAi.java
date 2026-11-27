package com.marketingagent.domain.subsidy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 지원사업 AI. 비용을 줄이려고 두 가지만 한다.
 * <ol>
 *   <li>공고 1건당 1회: 자격요건을 구조화 JSON 으로 추출(저렴한 모델). 회원 수만큼 부르지 않는다.</li>
 *   <li>규칙으로 애매(maybe)한 상위 건만 재판단(사업체당 하루 상한).</li>
 * </ol>
 * AI 를 쓰지 않는 서버(stub)에서는 지역만 규칙으로 뽑고 나머지는 null(모름)로 둔다.
 */
@Slf4j
@Component
public class SubsidyAi {

    private static final String EXTRACT_SYSTEM = """
            너는 한국 정부지원사업 공고문에서 신청 자격요건만 정확히 뽑는 분석기다.
            공고에 적힌 것만 쓴다. 적혀 있지 않으면 null 또는 빈 배열. 절대 추측하지 않는다.
            - regions: 신청 가능한 시·도 목록. 전국이면 ["전국"]. 언급이 없으면 null
            - entity_types: "법인","개인","예비창업자" 중 신청 가능한 것. 언급 없으면 null
            - min_biz_age_years / max_biz_age_years: 업력(창업 후 년수) 하한·상한. "창업 7년 이내" → max 7
            - preliminary_founder_ok: 예비창업자 신청 가능 여부. 언급 없으면 null
            - target_industries / excluded_industries: 대상·제외 업종 이름
            - max_revenue_krw: 매출 상한(원). max_employees: 상시근로자 상한
            - required_certs: 반드시 있어야 하는 인증(벤처, 이노비즈, 메인비즈, 여성기업 등)
            - preferred: 가점·우대 조건(청년대표, 여성기업 등)
            - ceo_age_max: 대표자 나이 상한(만 나이)
            - support_type: 자금|보증|R&D|사업화|마케팅|교육|인력|수출|기타 중 하나
            - amount_text: 지원 규모 문구 그대로(예: "최대 1억원"). 없으면 null
            - summary: 무엇을 누구에게 얼마나 지원하는지 3줄 요약(한국어)
            출력은 JSON 객체 하나만. 키 이름은 위와 같다.
            """;

    private static final String JUDGE_SYSTEM = """
            너는 한국 정부지원사업 상담사다. 사업체 정보와 공고·자격요건을 보고 이 사업체가 신청할 만한지 판단한다.
            확실하지 않으면 maybe. 공고에 없는 조건을 만들어 내지 않는다. 자금 알선·대행을 권하지 않는다.
            출력은 JSON 객체 하나만:
            {"verdict":"fit|maybe|no","score":0-100,"reasons":["왜 맞는지"],"blockers":["걸리는 조건·확인할 것"]}
            """;

    private final AiProvider aiProvider;
    private final SubsidyProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${ai.provider:stub}")
    private String providerName;

    public SubsidyAi(AiProvider aiProvider, SubsidyProperties properties, ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean available() {
        return !"stub".equalsIgnoreCase(providerName);
    }

    /** @return eligibility JSON 문자열. 실패하면 규칙 추출 결과 */
    public Eligibility extract(SubsidyAnnouncement a) {
        if (available()) {
            try {
                String user = "[제목] " + a.getTitle() + "\n[기관] " + nz(a.getAgency()) + "\n[지역 표시] "
                        + nz(a.getRegionText()) + "\n[신청기간] " + a.getApplyStart() + " ~ " + a.getApplyEnd()
                        + "\n[지원대상] " + nz(a.getTargetText()) + "\n[사업 개요]\n" + cut(a.getSummary(), 6000);
                String raw = aiProvider.generate(new AiRequest(EXTRACT_SYSTEM, user, properties.extractModel())).content();
                return objectMapper.readValue(jsonObject(raw), Eligibility.class);
            } catch (Exception e) {
                log.warn("공고 자격요건 추출 실패, 규칙으로 대신합니다: {} {}", a.getTitle(), e.getMessage());
            }
        }
        return byRules(a);
    }

    /** AI 없이: 지역 표시·제목에서 시도만 뽑는다. 나머지는 모름. */
    static Eligibility byRules(SubsidyAnnouncement a) {
        String text = nz(a.getRegionText()) + " " + nz(a.getTitle());
        List<String> regions = new ArrayList<>();
        if (text.contains("전국")) {
            regions.add("전국");
        } else {
            for (String s : List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
                    "충북", "충남", "전북", "전남", "경북", "경남", "제주")) {
                if (text.contains(s)) {
                    regions.add(s);
                }
            }
        }
        String summary = a.getSummary() == null ? null : cut(a.getSummary(), 300);
        return new Eligibility(regions.isEmpty() ? null : regions, null, null, null, null, null, null, null, null,
                null, null, null, null, null, summary);
    }

    public record Judgment(String verdict, int score, List<String> reasons, List<String> blockers) {}

    /** 애매한 건 재판단. 실패하면 null(규칙 결과 유지). */
    public Judgment judge(String profileText, SubsidyAnnouncement a, String eligibilityJson, EligibilityMatcher.Result rule) {
        if (!available()) {
            return null;
        }
        try {
            String user = "[사업체]\n" + profileText + "\n\n[공고] " + a.getTitle() + " (" + nz(a.getAgency()) + ")\n마감: "
                    + a.getApplyEnd() + "\n지원대상: " + nz(a.getTargetText()) + "\n개요: " + cut(a.getSummary(), 3000)
                    + "\n\n[추출한 자격요건]\n" + nz(eligibilityJson)
                    + "\n\n[규칙 판정] " + rule.verdict() + " " + rule.score() + "점, 이유 " + rule.reasons()
                    + ", 걸리는 것 " + rule.blockers();
            String raw = aiProvider.generate(new AiRequest(JUDGE_SYSTEM, user, properties.judgeModel())).content();
            JsonNode n = objectMapper.readTree(jsonObject(raw));
            String verdict = n.path("verdict").asText("maybe");
            if (!List.of(SubsidyMatch.FIT, SubsidyMatch.MAYBE, SubsidyMatch.NO).contains(verdict)) {
                verdict = SubsidyMatch.MAYBE;
            }
            List<String> reasons = new ArrayList<>();
            n.path("reasons").forEach(x -> reasons.add("AI: " + x.asText()));
            List<String> blockers = new ArrayList<>();
            n.path("blockers").forEach(x -> blockers.add("AI: " + x.asText()));
            return new Judgment(verdict, Math.max(0, Math.min(100, n.path("score").asInt(rule.score()))), reasons, blockers);
        } catch (Exception e) {
            log.warn("지원사업 재판단 실패: {} {}", a.getTitle(), e.getMessage());
            return null;
        }
    }

    static String jsonObject(String raw) {
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        if (s < 0 || e <= s) {
            throw new IllegalStateException("JSON 이 없습니다");
        }
        return raw.substring(s, e + 1);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.substring(0, Math.min(max, s.length()));
    }
}
