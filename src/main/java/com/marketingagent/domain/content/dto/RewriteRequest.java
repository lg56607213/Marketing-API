package com.marketingagent.domain.content.dto;

import jakarta.validation.constraints.NotBlank;

public record RewriteRequest(
        @NotBlank String instructions,
        String aiModel
) {}
