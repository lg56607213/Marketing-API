package com.marketingagent.domain.content.dto;

import com.marketingagent.common.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateRequest(
        @NotNull Long brandId,
        @NotNull ContentType contentType,
        @NotBlank String topic,
        String title,
        String aiModel
) {}
