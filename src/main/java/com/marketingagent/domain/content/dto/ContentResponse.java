package com.marketingagent.domain.content.dto;

import com.marketingagent.common.ContentStatus;
import com.marketingagent.common.ContentType;
import com.marketingagent.domain.content.Content;
import java.time.LocalDateTime;

public record ContentResponse(
        Long id,
        Long brandId,
        String brandName,
        ContentType contentType,
        ContentStatus status,
        String title,
        String topic,
        String body,
        String aiModel,
        Long authorId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContentResponse from(Content content) {
        return new ContentResponse(
                content.getId(),
                content.getBrand().getId(),
                content.getBrand().getName(),
                content.getContentType(),
                content.getStatus(),
                content.getTitle(),
                content.getTopic(),
                content.getBody(),
                content.getAiModel(),
                content.getAuthorId(),
                content.getCreatedAt(),
                content.getUpdatedAt()
        );
    }
}
