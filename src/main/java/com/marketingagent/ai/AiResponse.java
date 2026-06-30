package com.marketingagent.ai;

public record AiResponse(String content, int promptTokens, int completionTokens) {}
