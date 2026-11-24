package com.marketingagent.ai.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.marketingagent.ai.AiProvider;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Claude 로 답을 만든다. 목표 상담(답 읽기·종합 제안)과 글쓰기가 함께 쓴다.
 *
 * <p>적응형 사고(adaptive thinking)를 켠다. 상담은 사용자의 긴 설명에서 여러 값을 한꺼번에 읽어 내고
 * 정해진 JSON 으로 돌려줘야 해서, 모델이 생각할 여지가 있어야 한다. 깊이는 effort 로 조절한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "anthropic")
public class AnthropicProvider implements AiProvider {

    private static final String DEFAULT_MODEL = "claude-opus-5-5";

    private final AnthropicProperties properties;

    /**
     * 클라이언트는 첫 호출 때 만든다.
     *
     * <p>기동 시점에 만들면 키가 없을 때 애플리케이션 전체가 뜨지 않는다.
     * 글을 쓰려 할 때 "키가 없다" 고 말해 주는 편이 낫다.
     */
    private volatile AnthropicClient client;

    public AnthropicProvider(AnthropicProperties properties) {
        this.properties = properties;
    }

    private AnthropicClient client() {
        AnthropicClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = createClient();
                    client = local;
                }
            }
        }
        return local;
    }

    private AnthropicClient createClient() {
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            return AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
        }
        // 환경변수·OAuth 프로필·WIF 를 SDK 가 알아서 찾는다. 없으면 요청 때 401 로 드러난다.
        return AnthropicOkHttpClient.fromEnv();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        String model = request.model() != null && !request.model().isBlank()
                ? request.model()
                : orDefault(properties.model(), DEFAULT_MODEL);

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(properties.maxTokens() > 0 ? properties.maxTokens() : 16000L)
                .system(request.systemPrompt())
                .addUserMessage(request.userPrompt())
                .thinking(ThinkingConfigAdaptive.builder().build());

        effort().ifPresent(e -> builder.outputConfig(OutputConfig.builder().effort(e).build()));

        Message response;
        try {
            response = client().messages().create(builder.build());
        } catch (UnauthorizedException e) {
            // fromEnv() 는 키가 없어도 객체를 만들고, 실패는 요청 시점에 401 로 온다.
            // 여기서 잡아야 OAuth·WIF 같은 다른 자격증명 경로를 막지 않으면서 안내할 수 있다.
            throw new BadRequestException(
                    "Claude 인증에 실패했습니다. ANTHROPIC_API_KEY 를 설정했는지 확인하세요. "
                            + "키 없이 돌려보려면 AI_PROVIDER=stub 으로 두십시오.");
        }

        String text = response.content().stream()
                .map(ContentBlock::text)
                .filter(java.util.Optional::isPresent)
                .map(block -> block.get().text())
                .reduce("", (a, b) -> a + b)
                .strip();

        if (text.isEmpty()) {
            // 안전 분류기가 요청을 거절하면 200 으로 오되 본문이 비어 있다.
            String reason = response.stopReason().map(Object::toString).orElse("unknown");
            throw new IllegalStateException(
                    "Claude 응답이 비어 있습니다. stop_reason=" + reason
                            + "");
        }

        long input = response.usage().inputTokens();
        long output = response.usage().outputTokens();
        log.info("Claude 생성 완료: model={} 입력={}토큰 출력={}토큰", model, input, output);

        return new AiResponse(text, (int) input, (int) output);
    }

    private java.util.Optional<OutputConfig.Effort> effort() {
        String value = properties.effort();
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        // Effort 는 자바 enum 이 아니라 값 클래스다. of() 로 만들고 유효성은 따로 확인한다.
        OutputConfig.Effort effort = OutputConfig.Effort.of(value.strip().toLowerCase(Locale.ROOT));
        if (!effort.isValid()) {
            log.warn("알 수 없는 effort 값이라 기본값을 씁니다: {}", value);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(effort);
    }

    private String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
