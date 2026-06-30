package com.marketingagent.domain.template.dto;

import com.marketingagent.common.ContentType;
import com.marketingagent.domain.template.Template;
import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String name,
        Long brandId,
        String brandName,
        ContentType contentType,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TemplateResponse from(Template template) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getBrand() != null ? template.getBrand().getId() : null,
                template.getBrand() != null ? template.getBrand().getName() : null,
                template.getContentType(),
                template.getBody(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
