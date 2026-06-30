package com.marketingagent.domain.prompt.dto;

import com.marketingagent.common.ContentType;
import com.marketingagent.domain.prompt.PromptTemplate;
import java.time.LocalDateTime;

public record PromptResponse(
        Long id,
        String name,
        Long brandId,
        String brandName,
        ContentType contentType,
        String systemPrompt,
        String userPromptTemplate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PromptResponse from(PromptTemplate pt) {
        return new PromptResponse(
                pt.getId(),
                pt.getName(),
                pt.getBrand() != null ? pt.getBrand().getId() : null,
                pt.getBrand() != null ? pt.getBrand().getName() : null,
                pt.getContentType(),
                pt.getSystemPrompt(),
                pt.getUserPromptTemplate(),
                pt.getCreatedAt(),
                pt.getUpdatedAt()
        );
    }
}
