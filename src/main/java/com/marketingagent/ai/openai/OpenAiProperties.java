package com.marketingagent.ai.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.openai")
public record OpenAiProperties(String apiKey, String model, String baseUrl) {}
