package com.marketingagent.ai.openai;

import com.marketingagent.ai.AiProvider;
import com.marketingagent.ai.AiRequest;
import com.marketingagent.ai.AiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
@RequiredArgsConstructor
public class OpenAiProvider implements AiProvider {

    private final OpenAiProperties openAiProperties;

    @Override
    public AiResponse generate(AiRequest request) {
        RestClient client = RestClient.builder()
                .baseUrl(openAiProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        String model = request.model() != null ? request.model() : openAiProperties.model();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())
                )
        );

        OpenAiChatResponse response = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(OpenAiChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Empty response from OpenAI");
        }

        String content = response.choices().get(0).message().content();
        int promptTokens = response.usage() != null ? response.usage().promptTokens() : 0;
        int completionTokens = response.usage() != null ? response.usage().completionTokens() : 0;
        return new AiResponse(content, promptTokens, completionTokens);
    }

    record OpenAiChatResponse(List<Choice> choices, Usage usage) {
        record Choice(Message message) {}
        record Message(String role, String content) {}
        record Usage(
                @JsonProperty("prompt_tokens") int promptTokens,
                @JsonProperty("completion_tokens") int completionTokens
        ) {}
    }
}
