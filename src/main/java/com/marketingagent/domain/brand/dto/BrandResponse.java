package com.marketingagent.domain.brand.dto;

import com.marketingagent.domain.brand.Brand;
import java.time.LocalDateTime;

public record BrandResponse(
        Long id,
        String name,
        String description,
        String toneAndManner,
        String cta,
        String forbiddenWords,
        String allowedWords,
        String seoRules,
        Long ownerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getToneAndManner(),
                brand.getCta(),
                brand.getForbiddenWords(),
                brand.getAllowedWords(),
                brand.getSeoRules(),
                brand.getOwnerId(),
                brand.getCreatedAt(),
                brand.getUpdatedAt()
        );
    }
}
