package com.marketingagent.domain.template.dto;

import com.marketingagent.common.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateRequest(
        @NotBlank String name,
        @NotNull Long brandId,
        @NotNull ContentType contentType,
        @NotBlank String body
) {}
