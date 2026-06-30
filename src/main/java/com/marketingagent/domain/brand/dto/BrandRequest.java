package com.marketingagent.domain.brand.dto;

import jakarta.validation.constraints.NotBlank;

public record BrandRequest(
        @NotBlank String name,
        String description,
        String toneAndManner,
        String cta,
        String forbiddenWords,
        String allowedWords,
        String seoRules
) {}
