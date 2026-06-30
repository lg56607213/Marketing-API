package com.marketingagent.domain.prompt.dto;

import com.marketingagent.common.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PromptRequest(
        @NotBlank String name,
        @NotNull Long brandId,
        @NotNull ContentType contentType,
        @NotBlank String systemPrompt,
        @NotBlank String userPromptTemplate
) {}
