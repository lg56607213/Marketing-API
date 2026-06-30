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
        String content = """
                [Stub AI] 생성된 마케팅 콘텐츠입니다.

                요청 프롬프트: %s

                이 텍스트는 실제 AI 없이 테스트 목적으로 생성된 예시 콘텐츠입니다.
                실제 환경에서는 OpenAI GPT 모델이 브랜드 톤앤매너에 맞는 콘텐츠를 생성합니다.
                """.formatted(request.userPrompt());
        return new AiResponse(content, 100, 150);
    }
}
