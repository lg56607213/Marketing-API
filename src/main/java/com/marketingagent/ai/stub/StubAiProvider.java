package com.marketingagent.ai.stub;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubAiProvider implements AiProvider {

    @Override
    public AiResponse generate(AiRequest request) {
        String userPrompt = request.userPrompt();
        String topic = extractField(userPrompt, "주제:");
        String brand = extractField(userPrompt, "브랜드:");
        String contentType = extractField(userPrompt, "콘텐츠 유형:");
        String style = extractStyleHint(userPrompt);

        String content = generateContent(topic, brand, contentType, style);
        return new AiResponse(content, 300, 1200);
    }

    private String extractField(String prompt, String prefix) {
        int start = prompt.indexOf(prefix);
        if (start < 0) return "";
        start += prefix.length();
        int end = prompt.indexOf('\n', start);
        String raw = end < 0 ? prompt.substring(start) : prompt.substring(start, end);
        // "담보대출\n브랜드:" 같은 경우 다음 필드 레이블 전까지만
        for (String next : new String[]{"브랜드:", "콘텐츠 유형:", "CTA:", "스타일:", "주제:"}) {
            int idx = raw.indexOf(next);
            if (idx > 0) raw = raw.substring(0, idx);
        }
        return raw.trim().replaceFirst("\\s*에 맞는.*", "").trim();
    }

    private String extractStyleHint(String prompt) {
        if (prompt.contains("친근") || prompt.contains("대화체")) return "friendly";
        if (prompt.contains("광고") || prompt.contains("설득")) return "ad";
        if (prompt.contains("스토리")) return "story";
        if (prompt.contains("전문가") || prompt.contains("기술")) return "expert";
        return "professional";
    }

    private String generateContent(String topic, String brand, String contentType, String style) {
        String ct = contentType.toUpperCase();
        if (ct.startsWith("BLOG")) return generateBlog(topic, brand);
        if (ct.startsWith("SNS")) return generateSns(topic, brand);
        if (ct.startsWith("CARDNEWS")) return generateCardNews(topic, brand);
        if (ct.startsWith("EMAIL")) return generateEmail(topic, brand);
        if (ct.startsWith("YOUTUBE")) return generateYoutube(topic, brand);
        if (ct.startsWith("LANDING")) return generateLandingPage(topic, brand);
        return generateBlog(topic, brand);
    }

    // ── BLOG ──────────────────────────────────────────────────────────────────

    private String generateBlog(String topic, String brand) {
        String t = topic.isEmpty() ? "금융 상품" : topic;
        String b = brand.isEmpty() ? "저희 회사" : brand;
        return """
                # %s 완벽 가이드: 알아야 할 모든 것

                ## 들어가며

                최근 금융 시장에서 %s에 대한 관심이 빠르게 높아지고 있습니다. 금리 변동과 부동산 정책이 맞물리면서 올바른 금융 상품을 선택하는 것이 그 어느 때보다 중요해졌습니다. 이 글에서는 %s의 핵심 개념부터 신청 절차, 주의사항까지 실무에서 바로 활용할 수 있는 정보를 체계적으로 정리했습니다.

                ---

                ## 1. %s란 무엇인가?

                %s은(는) 자산을 담보로 제공하고 그에 상응하는 금액을 대출받는 금융 상품입니다. 신용 대출과 달리 담보가 있기 때문에 상대적으로 낮은 금리로 큰 금액을 조달할 수 있다는 것이 가장 큰 장점입니다.

                **주요 특징:**
                - 낮은 금리: 신용 대출 대비 연 1~3%%p 낮은 금리 적용 가능
                - 높은 한도: 담보 가치의 최대 70~80%%까지 대출 가능
                - 장기 상환: 최대 30~40년 장기 상환 설계 가능
                - 다양한 금리 유형: 고정금리, 변동금리, 혼합금리 선택 가능

                ---

                ## 2. 규제지역과 비규제지역의 차이

                정부는 부동산 시장 안정을 위해 특정 지역을 **규제지역**으로 지정하고 있습니다. 규제지역 여부에 따라 담보인정비율(LTV)과 총부채상환비율(DTI)이 달라지므로 반드시 확인이 필요합니다.

                | 구분 | 규제지역 | 비규제지역 |
                |------|----------|------------|
                | LTV  | 최대 40~50%% | 최대 60~70%% |
                | DTI  | 40%% 이하 | 50~60%% |
                | DSR  | 40%% 적용 | 40%% 적용 |
                | 주택 수 | 1주택자 우선 | 다주택 가능 |

                > **핵심 포인트:** 규제지역에서는 투기 수요 억제를 위해 대출 한도가 상당히 제한됩니다. 매물이 규제지역에 해당하는지 사전에 반드시 확인하세요.

                ---

                ## 3. 신청 자격과 필요 서류

                ### 자격 요건

                1. **소득 요건**: 안정적인 소득 증빙이 가능한 근로자, 자영업자, 법인 사업자
                2. **신용 요건**: 신용점수 700점 이상 권장 (금융기관별 상이)
                3. **담보 요건**: 감정가 기준 적정 가치 보유 부동산

                ### 준비 서류

                - 신분증 사본
                - 소득 증빙 서류 (근로소득: 근로소득원천징수영수증 / 자영업: 사업소득확인서)
                - 재직증명서 (해당자)
                - 부동산 등기부등본 (3개월 이내 발급)
                - 건물 건축물대장

                ---

                ## 4. 금리 유형별 장단점 비교

                ### 고정금리

                대출 기간 내내 동일한 금리가 적용됩니다. 금리 상승기에 유리하며, 매월 상환액이 일정해 가계 재무 계획을 세우기 쉽습니다.

                ### 변동금리

                시장 기준금리(COFIX, CD금리 등)에 연동되어 3~6개월마다 변동됩니다. 금리 하락기에 이자 비용을 절감할 수 있으나, 금리 상승 시 부담이 커질 수 있습니다.

                ### 혼합금리 (추천)

                초기 3~5년은 고정금리, 이후 변동금리로 전환됩니다. 초기 금리 안정성과 장기 유연성을 모두 확보할 수 있어 최근 수요자들에게 가장 인기 있는 유형입니다.

                ---

                ## 5. 이자 절감 꿀팁

                **중도상환수수료 확인:** 대출 후 3년 이내 조기 상환 시 수수료(보통 잔액의 1~2%%)가 발생할 수 있습니다. 여유 자금이 있다면 수수료 면제 시점을 계산해 두세요.

                **대환 대출 활용:** 기존 고금리 대출을 저금리로 갈아타는 대환 대출을 적극 검토하세요. 금리 차이가 0.5%%p만 나도 수천만 원 단위 이자 절감 효과가 나타납니다.

                **우대금리 챙기기:** 급여 이체, 카드 실적, 자동이체 등록 등 금융기관이 제시하는 우대 조건을 꼼꼼히 확인하면 추가 0.1~0.5%%p 금리 인하가 가능합니다.

                ---

                ## 마치며

                %s은(는) 목돈 마련의 중요한 수단이지만, 잘못된 선택은 장기 재무 부담으로 이어질 수 있습니다. %s와 함께라면 여러분의 상황에 가장 적합한 조건으로 대출을 설계해 드립니다. 지금 바로 무료 상담을 통해 최적의 조건을 확인해 보세요.

                **%s 전문 상담 ☎ 1588-0000 | 평일 09:00~18:00**
                """.formatted(t, t, t, t, t, t, b, b);
    }

    // ── SNS ───────────────────────────────────────────────────────────────────

    private String generateSns(String topic, String brand) {
        String t = topic.isEmpty() ? "금융 상품" : topic;
        String b = brand.isEmpty() ? "저희 브랜드" : brand;
        return """
                📌 %s, 제대로 알고 계신가요?

                많은 분들이 %s를 알아보시다가
                복잡한 조건과 용어에 막혀 포기하시더라고요 😢

                그래서 오늘은 핵심만 딱 정리해 드립니다! 👇

                ✅ LTV · DTI · DSR 간단 이해
                ✅ 규제지역 vs 비규제지역 차이
                ✅ 고정 vs 변동 금리 선택 기준
                ✅ 중도상환 수수료 절감 방법

                복잡하게 느껴지셨다면, 이제 %s가 옆에서 쉽게 설명해 드릴게요!

                📞 지금 바로 무료 상담 신청하기
                링크는 프로필에 있습니다 🔗

                #%s #금융상담 #대출정보 #재테크 #부동산금융
                """.formatted(t, t, b, t.replace(" ", ""));
    }

    // ── CARD NEWS ─────────────────────────────────────────────────────────────

    private String generateCardNews(String topic, String brand) {
        String t = topic.isEmpty() ? "금융 상품" : topic;
        String b = brand.isEmpty() ? "브랜드" : brand;
        return """
                📌 카드뉴스: %s 핵심 정리

                ─────────────────────────
                [Card 1]
                제목: %s, 왜 알아야 할까요?
                내용: 금리 상승기, 올바른 금융 선택이 자산을 지킵니다.
                ─────────────────────────
                [Card 2]
                제목: 담보 vs 신용 대출
                내용:
                • 담보 대출: 낮은 금리, 높은 한도
                • 신용 대출: 빠른 승인, 무담보
                ─────────────────────────
                [Card 3]
                제목: LTV · DTI · DSR 30초 이해
                내용:
                • LTV = 집값 대비 대출 비율
                • DTI = 연소득 대비 연간 원리금 비율
                • DSR = 모든 대출의 원리금 / 연소득
                ─────────────────────────
                [Card 4]
                제목: 규제지역 체크 포인트
                내용:
                • 투기과열지구 LTV 최대 40%%
                • 조정대상지역 LTV 최대 50%%
                • 지정 여부 국토부 홈페이지에서 확인 가능
                ─────────────────────────
                [Card 5]
                제목: 금리 유형 선택 가이드
                내용:
                금리 상승 예상 → 고정금리
                금리 하락 예상 → 변동금리
                불확실하다면 → 혼합금리(5년 고정)
                ─────────────────────────
                [Card 6 - CTA]
                제목: %s와 함께 시작하세요
                내용: 지금 무료 상담 신청 시
                금리 비교 리포트를 무료로 드립니다!
                📞 1588-0000
                ─────────────────────────
                """.formatted(t, t, b);
    }

    // ── EMAIL ─────────────────────────────────────────────────────────────────

    private String generateEmail(String topic, String brand) {
        String t = topic.isEmpty() ? "금융 상품" : topic;
        String b = brand.isEmpty() ? "저희 회사" : brand;
        return """
                제목: [%s] 지금이 바로 최적의 타이밍입니다

                안녕하세요, 고객님.

                %s를 검토하고 계신가요?

                최근 기준금리 변동과 함께 %s 조건이 크게 바뀌고 있습니다.
                지금 시점을 놓치면 불리한 조건으로 대출을 받을 수도 있습니다.

                ─────────────────────────
                ✅ 현재 %s 주요 혜택

                1. 최저 연 %% 3.X대 금리 (우대 조건 충족 시)
                2. 최대 10억 원까지 한도 가능
                3. 최장 40년 분할 상환
                4. 전문 상담사 1:1 맞춤 설계
                ─────────────────────────

                고객님의 상황에 맞는 최적 조건을 찾아드리기 위해
                %s 전담 상담팀이 기다리고 있습니다.

                📞 무료 상담 신청: 1588-0000
                🌐 온라인 신청: www.%s.co.kr

                상담 후 마음에 들지 않으셔도 부담 없이 거절하실 수 있습니다.
                지금 바로 연락 주세요!

                감사합니다.
                %s 드림
                """.formatted(t, t, t, t, b, b.replaceAll(" ", ""), b);
    }

    // ── YOUTUBE SCRIPT ────────────────────────────────────────────────────────

    private String generateYoutube(String topic, String brand) {
        String t = topic.isEmpty() ? "금융 상품" : topic;
        String b = brand.isEmpty() ? "저희 채널" : brand;
        return """
                🎬 유튜브 영상 스크립트: %s 완전 정복

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [인트로 - 0:00~0:30]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━

                (화면: 집 앞에서 고민하는 30대 직장인)

                내레이션:
                "내 집 마련의 꿈, 포기하셨나요?
                %s를 제대로 알면 길이 보입니다."

                (채널 인트로 영상)

                "안녕하세요! %s입니다.
                오늘은 많은 분들이 어렵게 느끼시는 %s에 대해
                10분 안에 핵심을 정리해 드리겠습니다."

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [본문 1 - LTV/DTI/DSR 설명 - 0:30~3:00]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━

                "먼저 세 가지 용어를 반드시 알아야 합니다."

                (화면: 인포그래픽 애니메이션)

                "첫 번째, LTV. Loan To Value의 약자로,
                집값 대비 얼마까지 빌릴 수 있는지를 나타냅니다.
                규제지역에서는 최대 40~50%%, 비규제지역은 70%%까지 가능합니다."

                "두 번째, DTI. 연간 소득 대비 원리금 상환 비율입니다.
                연봉 5천만 원이라면, DTI 40%% 기준 연간 2천만 원까지 원리금을 낼 수 있습니다."

                "세 번째, DSR. 요즘 가장 중요한 지표입니다.
                모든 대출의 연간 원리금 합계를 연소득으로 나눈 비율입니다.
                40%%를 초과하면 추가 대출이 어렵습니다."

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [본문 2 - 금리 선택 - 3:00~6:30]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━

                "이제 금리 유형을 선택해야 합니다.
                고정이냐, 변동이냐 — 이게 수천만 원 차이를 만들 수 있습니다."

                (화면: 금리 그래프 비교)

                "금리 상승이 예상된다면 고정금리를 선택하세요.
                반대로 하락이 예상된다면 변동금리가 유리합니다.
                불확실하다면 5년 고정 혼합금리가 가장 안전한 선택입니다."

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [CTA - 9:30~10:00]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━

                "오늘 영상이 도움이 되셨다면 구독과 좋아요 부탁드립니다!
                %s 무료 상담 링크는 아래 설명란에 있습니다.
                댓글로 궁금한 점 남겨주시면 답변 드릴게요. 감사합니다!"
                """.formatted(t, t, b, t, b);
    }

    // ── LANDING PAGE ──────────────────────────────────────────────────────────

    private String generateLandingPage(String topic, String brand) {
        String t = topic.isEmpty() ? "금융 상품" : topic;
        String b = brand.isEmpty() ? "저희 회사" : brand;
        return """
                ■ 랜딩 페이지 카피: %s

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [히어로 섹션]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                헤드라인:
                "%s, 지금 가장 유리한 조건으로 시작하세요"

                서브 카피:
                "복잡한 금융 상품, 전문가와 함께라면 쉽습니다.
                %s의 1:1 맞춤 상담으로 최저금리 조건을 찾아드립니다."

                CTA 버튼: [무료 상담 신청하기]

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [혜택 섹션]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                ✅ 최저 연 3.X%% 금리 적용
                ✅ 최대 한도 10억 원
                ✅ 최장 40년 분할 상환
                ✅ 당일 결과 안내
                ✅ 전국 어디서나 비대면 신청 가능

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [신뢰 지표 섹션]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                📊 누적 대출 실행 1조 원 돌파
                👥 고객 만족도 98.7%%
                🏆 2024 금융 서비스 대상 수상

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [FAQ 섹션]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                Q. 규제지역도 대출이 가능한가요?
                A. 네, 가능합니다. 다만 LTV가 40~50%%로 제한됩니다. 정확한 한도는 상담을 통해 확인하세요.

                Q. 신용점수가 낮아도 신청할 수 있나요?
                A. 담보가 있기 때문에 신용 대출보다 조건이 유연합니다. 전문 상담사가 최적 상품을 안내해 드립니다.

                Q. 상담은 무료인가요?
                A. 네, 상담부터 조건 확인까지 모두 무료입니다.

                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                [최종 CTA]
                ━━━━━━━━━━━━━━━━━━━━━━━━━━
                "지금 바로 무료 상담을 신청하세요.
                빠른 검토 후 24시간 이내 연락드립니다."

                [무료 상담 신청하기] ← 버튼
                📞 1588-0000
                """.formatted(t, t, b);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
