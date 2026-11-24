package com.marketingagent.ai.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey    ANTHROPIC_API_KEY
 * @param model     기본 claude-opus-5-5
 * @param maxTokens 응답 상한
 * @param effort    low | medium | high | xhigh | max. 생각 깊이와 토큰 소모를 함께 조절한다.
 */
@ConfigurationProperties("ai.anthropic")
public record AnthropicProperties(
        String apiKey,
        String model,
        long maxTokens,
        String effort
) {}
