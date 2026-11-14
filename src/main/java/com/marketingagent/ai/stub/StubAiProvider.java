package com.marketingagent.ai.stub;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 연동 없이 화면과 저장 흐름을 확인하기 위한 견본 생성기.
 *
 * <p>글을 쓰는 것이 아니라, 받은 주제와 브랜드 이름을 끼워 넣은 틀을 돌려준다. 어떤 업종의 주제가 와도 내용을 지어내지 않도록
 * 특정 업종의 사실·수치·상품 설명은 넣지 않는다. 실제 글은 ai.provider 를 실제 모델로 바꿔야 나온다.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubAiProvider implements AiProvider {

    private static final String NOTICE = "※ 견본입니다. AI 가 연결되지 않아 주제와 브랜드 이름만 넣은 틀을 보여 줍니다. "
            + "실제 글은 AI 연동(OPENAI_API_KEY)을 켠 뒤 생성됩니다.";

    @Override
    public AiResponse generate(AiRequest request) {
        String userPrompt = request.userPrompt() == null ? "" : request.userPrompt();
        String topic = orDefault(extractField(userPrompt, "주제:"), "주제");
        String brand = orDefault(extractField(userPrompt, "브랜드:"), "우리 브랜드");
        String contentType = extractField(userPrompt, "콘텐츠 유형:");
        return new AiResponse(generateContent(topic, brand, contentType), 300, 1200);
    }

    private String extractField(String prompt, String prefix) {
        int start = prompt.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = prompt.indexOf('\n', start);
        String raw = end < 0 ? prompt.substring(start) : prompt.substring(start, end);
        // 한 줄에 여러 항목이 이어진 경우 다음 항목 이름 앞까지만 쓴다.
        for (String next : new String[] {"브랜드:", "콘텐츠 유형:", "CTA:", "스타일:", "주제:"}) {
            int idx = raw.indexOf(next);
            if (idx > 0) {
                raw = raw.substring(0, idx);
            }
        }
        return raw.trim().replaceFirst("\\s*에 맞는.*", "").trim();
    }

    private String generateContent(String topic, String brand, String contentType) {
        String type = contentType == null ? "" : contentType.toUpperCase();
        if (type.startsWith("SNS")) {
            return sns(topic, brand);
        }
        if (type.startsWith("CARDNEWS")) {
            return cardNews(topic, brand);
        }
        if (type.startsWith("EMAIL")) {
            return email(topic, brand);
        }
        if (type.startsWith("YOUTUBE")) {
            return youtube(topic, brand);
        }
        if (type.startsWith("LANDING")) {
            return landing(topic, brand);
        }
        return blog(topic, brand);
    }

    private String blog(String topic, String brand) {
        return """
                %s

                # %s, 무엇부터 알아야 할까

                [도입] 독자가 '%s'을(를) 검색하게 된 상황과 고민을 2~3문장으로 적습니다.

                ## 1. 먼저 확인할 것
                [본문] '%s'을(를) 알아볼 때 사람들이 가장 먼저 궁금해하는 점을 설명합니다.

                ## 2. 고를 때 비교할 기준
                [본문] 가격, 조건, 기간처럼 비교에 쓰이는 기준을 3가지 안팎으로 정리합니다.

                ## 3. 자주 하는 실수
                [본문] 처음 알아보는 사람이 놓치기 쉬운 점을 사례와 함께 적습니다.

                ## %s이(가) 도와드릴 수 있는 것
                [본문] %s의 강점을 과장 없이 구체적으로 적습니다.

                [마무리] 다음 행동(문의, 견적 요청, 상담 예약)을 한 문장으로 안내합니다.
                """.formatted(NOTICE, topic, topic, topic, brand, brand);
    }

    private String sns(String topic, String brand) {
        return """
                %s

                [첫 줄] '%s' 알아보는 중이라면 이것만은 확인하세요.

                [본문 3줄]
                1) 가장 많이 묻는 점 한 가지
                2) 비교할 때 볼 기준 한 가지
                3) 놓치기 쉬운 점 한 가지

                [행동 유도] 자세한 내용은 프로필 링크에서. %s

                #%s #%s
                """.formatted(NOTICE, topic, brand, topic.replace(" ", ""), brand.replace(" ", ""));
    }

    private String cardNews(String topic, String brand) {
        return """
                %s

                [1장 표지] %s, 3분 만에 정리
                [2장 문제] 독자가 겪는 상황 한 문장
                [3장 핵심 1] 먼저 확인할 것
                [4장 핵심 2] 비교할 기준
                [5장 핵심 3] 자주 하는 실수
                [6장 정리] 세 가지를 한 장에 요약
                [7장 마무리] %s 문의 방법과 연락처
                """.formatted(NOTICE, topic, brand);
    }

    private String email(String topic, String brand) {
        return """
                %s

                제목: %s, 지금 알아보고 계신가요?

                안녕하세요, %s입니다.

                [도입] 받는 사람이 '%s'에 관심을 가질 만한 이유를 한두 문장으로 적습니다.

                [본문] 도움이 되는 정보 2~3가지를 짧은 문단으로 나눠 적습니다.

                [행동 유도] 문의하기 / 견적 받기 버튼 문구와 연결 주소

                %s 드림
                """.formatted(NOTICE, topic, brand, topic, brand);
    }

    private String youtube(String topic, String brand) {
        return """
                %s

                [영상 제목] %s, 처음이라면 이 영상 하나로

                [0:00 도입] 시청자가 겪는 상황을 한 문장으로 말하고 영상에서 다룰 내용을 예고합니다.
                [0:20 본론 1] 먼저 확인할 것
                [1:30 본론 2] 비교할 기준
                [2:40 본론 3] 자주 하는 실수
                [3:40 정리] 세 가지를 다시 짚습니다.
                [4:00 마무리] %s 소개와 문의 방법, 구독 요청

                [설명란] '%s' 관련 정리 영상입니다. 문의: %s
                """.formatted(NOTICE, topic, brand, topic, brand);
    }

    private String landing(String topic, String brand) {
        return """
                %s

                [머리말] %s, %s에서 쉽고 빠르게

                [부제] 방문자가 얻는 것을 한 문장으로 적습니다.

                [버튼] 무료 상담 신청

                [이유 3가지]
                - 강점 1: 구체적인 사실로
                - 강점 2: 구체적인 사실로
                - 강점 3: 구체적인 사실로

                [이용 순서] 1. 문의 → 2. 상담 → 3. 진행

                [자주 묻는 질문] '%s'에 대해 실제로 많이 받는 질문 3가지와 답

                [마지막 버튼] 지금 문의하기
                """.formatted(NOTICE, topic, brand, topic);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
